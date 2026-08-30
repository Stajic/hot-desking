package rs.ftn.hotdesk.server.service

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import rs.ftn.hotdesk.server.db.BookingSlots
import rs.ftn.hotdesk.server.db.Bookings
import rs.ftn.hotdesk.server.db.Resources
import rs.ftn.hotdesk.server.db.Users
import rs.ftn.hotdesk.shared.model.AvailabilityDto
import rs.ftn.hotdesk.shared.model.BookingDto
import rs.ftn.hotdesk.shared.model.BookingStatus
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.SlotDto
import rs.ftn.hotdesk.shared.rules.BookingRules
import java.util.UUID

sealed interface BookingResult {
    data class Created(val booking: BookingDto) : BookingResult
    data class Invalid(val code: String, val message: String) : BookingResult
    data object ResourceNotFound : BookingResult
    data object ResourceInactive : BookingResult

    /** Neko je bio brzi. Vraca se kao HTTP 409. */
    data object SlotTaken : BookingResult
}

object BookingService {

    fun create(userId: String, req: CreateBookingRequest, now: Long = System.currentTimeMillis()): BookingResult {
        // Korak 1: pravila iz :shared modula. Ista funkcija koju je klijent vec pozvao.
        // Klijentu se ne veruje - njegova provera je samo za brzinu odziva UI-ja.
        when (val v = BookingRules.validate(req.startTime, req.endTime, now)) {
            is BookingRules.Result.Invalid -> return BookingResult.Invalid(v.code, v.message)
            BookingRules.Result.Valid -> Unit
        }

        val slots = BookingRules.slotStarts(req.startTime, req.endTime)

        // Korak 2: upis. Nema provere zauzetosti - namerno.
        //
        // Naivna varijanta bi ovde uradila SELECT nad booking_slots i tek onda INSERT.
        // Izmedju ta dva koraka drugi zahtev moze da upise iste slotove, pa bi obe
        // rezervacije prosle. Klasican TOCTOU (time-of-check to time-of-use) problem.
        //
        // Umesto toga: pisemo odmah i pustamo jedinstveni indeks da presudi.
        // Izuzetak se namerno propusta IZVAN transaction bloka - da bi Exposed
        // uradio rollback i ponistio i zaglavlje rezervacije. Da ga uhvatimo unutra
        // i vratimo normalno, transakcija bi se KOMITOVALA i ostalo bi zaglavlje
        // bez slotova. To je najlakse mesto za gresku u celom resenju.
        return try {
            transaction {
                val resource = Resources.selectAll()
                    .where { Resources.id eq req.resourceId }
                    .singleOrNull() ?: return@transaction BookingResult.ResourceNotFound

                if (!resource[Resources.isActive]) {
                    return@transaction BookingResult.ResourceInactive
                }

                val user = Users.selectAll()
                    .where { Users.id eq userId }
                    .single()

                val bookingId = UUID.randomUUID().toString()

                Bookings.insert {
                    it[id] = bookingId
                    it[resourceId] = req.resourceId
                    it[Bookings.userId] = userId
                    it[startTime] = req.startTime
                    it[endTime] = req.endTime
                    it[status] = BookingStatus.CONFIRMED.name
                    it[createdAt] = now
                }

                BookingSlots.batchInsert(slots) { slot ->
                    this[BookingSlots.bookingId] = bookingId
                    this[BookingSlots.resourceId] = req.resourceId
                    this[BookingSlots.slotStart] = slot
                }

                BookingResult.Created(
                    BookingDto(
                        id = bookingId,
                        resourceId = req.resourceId,
                        resourceName = resource[Resources.name],
                        userId = userId,
                        userName = user[Users.name],
                        startTime = req.startTime,
                        endTime = req.endTime,
                        status = BookingStatus.CONFIRMED
                    )
                )
            }
        } catch (e: ExposedSQLException) {
            // SQLState 23xxx = integrity constraint violation, isto na H2 i PostgreSQL-u.
            if (e.sqlState?.startsWith("23") == true) BookingResult.SlotTaken else throw e
        }
    }

    /**
     * Otkazivanje: brisu se redovi iz booking_slots (slot se oslobadja),
     * zaglavlje ostaje sa statusom CANCELLED (istorija se cuva).
     */
    fun cancel(bookingId: String, userId: String, isAdmin: Boolean): Boolean = transaction {
        val row = Bookings.selectAll()
            .where { Bookings.id eq bookingId }
            .singleOrNull() ?: return@transaction false

        if (!isAdmin && row[Bookings.userId] != userId) return@transaction false
        if (row[Bookings.status] == BookingStatus.CANCELLED.name) return@transaction true

        BookingSlots.deleteWhere { BookingSlots.bookingId eq bookingId }
        Bookings.update({ Bookings.id eq bookingId }) {
            it[status] = BookingStatus.CANCELLED.name
        }
        true
    }

    /** Dnevna mreza slotova za jedan resurs - ono sto klijent crta kao grid. */
    fun availability(resourceId: String, anyTimeInDay: Long): AvailabilityDto = transaction {
        val dayStart = BookingRules.dayStart(anyTimeInDay)
        val count = BookingRules.slotsPerDay()
        val dayEnd = dayStart + count * BookingRules.SLOT_MILLIS

        val taken = BookingSlots.selectAll()
            .where {
                (BookingSlots.resourceId eq resourceId) and
                    (BookingSlots.slotStart greaterEq dayStart) and
                    (BookingSlots.slotStart less dayEnd)
            }
            .map { it[BookingSlots.slotStart] }
            .toSet()

        AvailabilityDto(
            resourceId = resourceId,
            dayStart = dayStart,
            slots = (0 until count).map { i ->
                val s = dayStart + i * BookingRules.SLOT_MILLIS
                SlotDto(
                    startTime = s,
                    endTime = s + BookingRules.SLOT_MILLIS,
                    isAvailable = s !in taken
                )
            }
        )
    }

    fun forUser(userId: String): List<BookingDto> = transaction {
        (Bookings innerJoin Resources innerJoin Users)
            .selectAll()
            .where { Bookings.userId eq userId }
            .orderBy(Bookings.startTime)
            .map { it.toBookingDto() }
    }

    /** Globalni pregled za administratora, sa opcionim filterima. */
    fun all(userId: String? = null, resourceId: String? = null): List<BookingDto> = transaction {
        var q = (Bookings innerJoin Resources innerJoin Users).selectAll()
        if (userId != null) q = q.andWhere { Bookings.userId eq userId }
        if (resourceId != null) q = q.andWhere { Bookings.resourceId eq resourceId }
        q.orderBy(Bookings.startTime).map { it.toBookingDto() }
    }
}
