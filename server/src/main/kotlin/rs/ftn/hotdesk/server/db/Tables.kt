package rs.ftn.hotdesk.server.db

import org.jetbrains.exposed.v1.core.Table

object Users : Table("users") {
    val id = varchar("id", 36)
    val name = varchar("name", 120)
    val email = varchar("email", 160).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val role = varchar("role", 16)
    override val primaryKey = PrimaryKey(id)
}

object Resources : Table("resources") {
    val id = varchar("id", 36)
    val name = varchar("name", 120)
    val type = varchar("type", 20)
    val location = varchar("location", 120)
    val capacity = integer("capacity")
    val isActive = bool("is_active").default(true)
    override val primaryKey = PrimaryKey(id)
}

/** Oprema resursa. Zasebna tabela umesto CSV kolone - filter "sala sa projektorom" je JOIN. */
object ResourceAmenities : Table("resource_amenities") {
    val resourceId = varchar("resource_id", 36).references(Resources.id)
    val amenity = varchar("amenity", 60)
    override val primaryKey = PrimaryKey(resourceId, amenity)
}

/**
 * Zaglavlje rezervacije. Nosi identitet, vlasnika, status i istoriju.
 * Ne nosi garanciju o zauzetosti - to je posao tabele [BookingSlots].
 */
object Bookings : Table("bookings") {
    val id = varchar("id", 36)
    val resourceId = varchar("resource_id", 36).references(Resources.id)
    val userId = varchar("user_id", 36).references(Users.id)
    val startTime = long("start_time")
    val endTime = long("end_time")
    val status = varchar("status", 16)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_bookings_user", false, userId)
        index("ix_bookings_resource_start", false, resourceId, startTime)
    }
}

/**
 * SRZ RESENJA PROBLEMA KONKURENTNOSTI.
 *
 * Jedan red = jedan zauzet 30-minutni slot. Rezervacija od 90 minuta upisuje tri reda.
 *
 * Jedinstveni indeks nad (resource_id, slot_start) cini preklapanje FIZICKI NEMOGUCIM,
 * nezavisno od toga koliko paralelnih zahteva stigne. Nema provere "da li je slobodno"
 * koja bi mogla da zastari izmedju citanja i upisa - baza je jedini arbitar.
 *
 * Prednost nad alternativama:
 *  - nema SELECT ... FOR UPDATE, pa nema zakljucavanja reda resursa;
 *  - nema SERIALIZABLE izolacije, pa nema retry petlje;
 *  - radi identicno na H2 i na PostgreSQL-u (za razliku od PostgreSQL-ovog
 *    EXCLUDE USING gist nad tstzrange, koji je elegantniji ali ga H2 nema).
 *
 * Otkazivanje brise redove odavde, a zaglavlju postavlja status CANCELLED.
 * Slot se time oslobadja, istorija ostaje, a indeks ostaje cist - bez parcijalnih
 * indeksa, koje H2 ne podrzava.
 */
object BookingSlots : Table("booking_slots") {
    val bookingId = varchar("booking_id", 36).references(Bookings.id)
    val resourceId = varchar("resource_id", 36)
    val slotStart = long("slot_start")

    init {
        uniqueIndex("uq_resource_slot", resourceId, slotStart)
        index("ix_slots_booking", false, bookingId)
    }
}
