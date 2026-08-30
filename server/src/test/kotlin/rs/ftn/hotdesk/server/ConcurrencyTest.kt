package rs.ftn.hotdesk.server

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.ftn.hotdesk.server.db.BookingSlots
import rs.ftn.hotdesk.server.db.Bookings
import rs.ftn.hotdesk.server.db.ResourceAmenities
import rs.ftn.hotdesk.server.db.Resources
import rs.ftn.hotdesk.server.db.Users
import rs.ftn.hotdesk.server.service.BookingResult
import rs.ftn.hotdesk.server.service.BookingService
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.Role
import rs.ftn.hotdesk.shared.rules.BookingRules
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OVO JE MERLJIVI REZULTAT RADA.
 *
 * N korisnika istovremeno trazi isti termin nad istim resursom.
 * Ocekivanje: tacno jedan prolazi, ostali dobijaju SlotTaken (HTTP 409).
 *
 * Kako se koristi na odbrani / na vezbama:
 *  1. Zakomentarisati liniju `uniqueIndex("uq_resource_slot", ...)` u Tables.kt
 *     i umesto direktnog upisa uraditi SELECT-pa-INSERT u BookingService.create.
 *     Test pada - prolazi vise rezervacija. To je TOCTOU u praksi.
 *  2. Vratiti jedinstveni indeks. Test prolazi.
 *
 * Razlika izmedju ta dva pokretanja je poglavlje sa rezultatima.
 */
class ConcurrencyTest {

    private val resourceId = UUID.randomUUID().toString()
    private lateinit var userIds: List<String>

    private val paralelnihZahteva = 50

    @BeforeTest
    fun setup() {
        // Svaki test dobija svezu bazu u memoriji.
        Database.connect(
            url = "jdbc:h2:mem:test_${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )

        transaction {
            SchemaUtils.create(Users, Resources, ResourceAmenities, Bookings, BookingSlots)

            userIds = (1..paralelnihZahteva).map { i ->
                val uid = UUID.randomUUID().toString()
                Users.insert {
                    it[id] = uid
                    it[name] = "Korisnik $i"
                    it[email] = "korisnik$i@firma.rs"
                    it[passwordHash] = "x"
                    it[role] = Role.USER.name
                }
                uid
            }

            Resources.insert {
                it[id] = resourceId
                it[name] = "Sala Dunav"
                it[type] = ResourceType.MEETING_ROOM.name
                it[location] = "Sprat 1"
                it[capacity] = 8
                it[isActive] = true
            }
        }
    }

    @Test
    fun samo_jedan_od_N_paralelnih_zahteva_dobija_isti_termin() = kotlinx.coroutines.runBlocking {
        val now = System.currentTimeMillis()
        val start = BookingRules.dayStart(now + 24 * 3_600_000L) + 2 * BookingRules.SLOT_MILLIS
        val end = start + 2 * BookingRules.SLOT_MILLIS

        val req = CreateBookingRequest(resourceId, start, end)

        val results = coroutineScope {
            userIds.map { uid ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    BookingService.create(uid, req, now = now)
                }
            }.awaitAll()
        }

        val uspesnih = results.count { it is BookingResult.Created }
        val odbijenih = results.count { it is BookingResult.SlotTaken }

        println("Paralelnih zahteva: $paralelnihZahteva | prihvaceno: $uspesnih | odbijeno (409): $odbijenih")

        assertEquals(1, uspesnih, "Tacno jedna rezervacija sme da prodje.")
        assertEquals(paralelnihZahteva - 1, odbijenih, "Sve ostale moraju biti odbijene kao zauzete.")
    }

    @Test
    fun delimicno_preklapanje_takodje_pada() {
        val now = System.currentTimeMillis()
        val start = BookingRules.dayStart(now + 24 * 3_600_000L) + 4 * BookingRules.SLOT_MILLIS

        // 09:00-10:00 (dva slota)
        val prvi = BookingService.create(
            userIds[0],
            CreateBookingRequest(resourceId, start, start + 2 * BookingRules.SLOT_MILLIS),
            now
        )
        assertEquals(true, prvi is BookingResult.Created)

        // 09:30-10:30 - deli tacno jedan slot sa prethodnom. Mora da padne.
        val drugi = BookingService.create(
            userIds[1],
            CreateBookingRequest(
                resourceId,
                start + BookingRules.SLOT_MILLIS,
                start + 3 * BookingRules.SLOT_MILLIS
            ),
            now
        )
        assertEquals(BookingResult.SlotTaken, drugi)
    }

    @Test
    fun otkazana_rezervacija_oslobadja_termin() {
        val now = System.currentTimeMillis()
        val start = BookingRules.dayStart(now + 24 * 3_600_000L) + 6 * BookingRules.SLOT_MILLIS
        val req = CreateBookingRequest(resourceId, start, start + BookingRules.SLOT_MILLIS)

        val prvi = BookingService.create(userIds[0], req, now)
        assertEquals(true, prvi is BookingResult.Created)

        assertEquals(BookingResult.SlotTaken, BookingService.create(userIds[1], req, now))

        BookingService.cancel((prvi as BookingResult.Created).booking.id, userIds[0], isAdmin = false)

        val posleOtkazivanja = BookingService.create(userIds[1], req, now)
        assertEquals(true, posleOtkazivanja is BookingResult.Created)
    }
}
