package rs.ftn.hotdesk.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.ftn.hotdesk.server.db.BookingSlots
import rs.ftn.hotdesk.server.db.Bookings
import rs.ftn.hotdesk.server.db.ResourceAmenities
import rs.ftn.hotdesk.server.db.Resources
import rs.ftn.hotdesk.server.db.Users
import rs.ftn.hotdesk.server.service.BookingResult
import rs.ftn.hotdesk.server.service.BookingService
import rs.ftn.hotdesk.shared.model.BookingStatus
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.Role
import rs.ftn.hotdesk.shared.rules.BookingRules
import java.io.File
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/*
 * NEGATIVNI PROLAZ: sta bi se desilo bez jedinstvenog indeksa.
 *
 * README tvrdi da naivna provera-pa-upis propusta vise rezervacija istog termina kad
 * zahtevi stignu istovremeno (TOCTOU). Ovaj program to meri umesto da se tvrdi: u
 * svakoj rundi 50 korisnika istovremeno trazi isti termin, jednom kroz naivnu
 * implementaciju nad tabelom bez indeksa, a jednom nad pravom semom sa indeksom,
 * kao kontrola.
 *
 * Dve varijante:
 *  - BEZ BARIJERE: kao server danas. Svaka transakcija otvara novu vezu sa bazom
 *    (bez pool-a), pa zahtevi stizu do provere razvuceni u vremenu, koliko traje
 *    otvaranje veze. Kontrola je sam BookingService.
 *  - SA BARIJEROM: svaki zahtev prvo otvori transakciju i procita resurs i korisnika,
 *    pa ceka da svih 50 budu spremni, i tek onda proverava i upisuje. To je cista
 *    trka, bez uticaja otvaranja veze, pod istim uslovima na obe baze. Kontrola je
 *    ovde kopija koraka iz BookingService.create, jer barijera ne moze da se umetne
 *    u sam servis.
 *
 * Naivna implementacija i tabela bez indeksa postoje samo ovde, u test kodu;
 * BookingService.kt i Tables.kt se ne diraju.
 *
 * Ovo je merenje, ne test: trka je po prirodi nepredvidiva, pa test koji bi tvrdio
 * "naivna varijanta pada" ponekad ne bi pao. Rezultat je raspodela po rundama.
 *
 * Pokretanje: .\gradlew.bat :server:negativniProlaz [-Pbaza=postgres]
 */

private const val KORISNIKA = 50
private const val RUNDI = 20

/** Kao BookingSlots, ali NAMERNO bez uniqueIndex(resource_id, slot_start). */
private object NaiveBookingSlots : Table("naive_booking_slots") {
    val bookingId = varchar("booking_id", 36).references(Bookings.id)
    val resourceId = varchar("resource_id", 36)
    val slotStart = long("slot_start")
}

/**
 * Barijera jedne runde, za jedan zahtev.
 *
 * Exposed posle SQLException-a ponavlja ceo blok transakcije (podrazumevano do tri
 * pokusaja, DatabaseConfig.defaultMaxAttempts). Ponovljeni pokusaj ne sme ponovo da
 * ceka na barijeri - ostali zahtevi do nje vise ne dolaze - pa se ceka samo jednom.
 * Svaki pokusaj se broji, da bi ponavljanje bilo vidljivo u rezultatu.
 */
private class Start(private val barijera: CyclicBarrier?, private val pokusaji: AtomicInteger) {
    private var cekao = false

    fun pokusaj() {
        pokusaji.incrementAndGet()
    }

    fun sacekajOstale() {
        if (barijera == null || cekao) return
        cekao = true
        // Ogranicenje od 30 s: ako neki zahtev ne stigne do barijere, merenje pada
        // glasno umesto da visi.
        barijera.await(30, TimeUnit.SECONDS)
    }
}

private fun insertHeader(bookingId: String, userId: String, req: CreateBookingRequest, now: Long) {
    Bookings.insert {
        it[id] = bookingId
        it[resourceId] = req.resourceId
        it[Bookings.userId] = userId
        it[startTime] = req.startTime
        it[endTime] = req.endTime
        it[status] = BookingStatus.CONFIRMED.name
        it[createdAt] = now
    }
}

/**
 * Naivna rezervacija: isti koraci kao BookingService.create, uz proveru zauzetosti
 * pre upisa. Izmedju (1) i (2) drugi zahtev moze da upise isti termin.
 */
private fun naiveCreate(userId: String, req: CreateBookingRequest, now: Long, start: Start): Boolean =
    transaction {
        start.pokusaj()
        val slots = BookingRules.slotStarts(req.startTime, req.endTime)

        val resource = Resources.selectAll().where { Resources.id eq req.resourceId }.single()
        check(resource[Resources.isActive])
        Users.selectAll().where { Users.id eq userId }.single()

        start.sacekajOstale()

        // (1) PROVERA: da li je neki od trazenih slotova vec zauzet
        val zauzeto = NaiveBookingSlots.selectAll().where {
            (NaiveBookingSlots.resourceId eq req.resourceId) and (NaiveBookingSlots.slotStart inList slots)
        }.any()
        if (zauzeto) return@transaction false

        // (2) UPIS
        val bookingId = UUID.randomUUID().toString()
        insertHeader(bookingId, userId, req, now)
        slots.forEach { s ->
            NaiveBookingSlots.insert {
                it[NaiveBookingSlots.bookingId] = bookingId
                it[NaiveBookingSlots.resourceId] = req.resourceId
                it[slotStart] = s
            }
        }
        true
    }

/**
 * Kontrola za varijantu sa barijerom: koraci iz BookingService.create (upis bez
 * provere, u tabelu sa jedinstvenim indeksom), sa barijerom pre upisa. Narusavanje
 * ogranicenja (SQLState 23xxx) znaci "zauzeto", kao u servisu.
 */
private fun indexedCreate(userId: String, req: CreateBookingRequest, now: Long, start: Start): Boolean =
    try {
        transaction {
            start.pokusaj()
            val slots = BookingRules.slotStarts(req.startTime, req.endTime)
            Resources.selectAll().where { Resources.id eq req.resourceId }.single()
            Users.selectAll().where { Users.id eq userId }.single()

            start.sacekajOstale()

            val bookingId = UUID.randomUUID().toString()
            insertHeader(bookingId, userId, req, now)
            BookingSlots.batchInsert(slots) { s ->
                this[BookingSlots.bookingId] = bookingId
                this[BookingSlots.resourceId] = req.resourceId
                this[BookingSlots.slotStart] = s
            }
            true
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState.startsWith("23") == true) false else throw e
    }

private fun isolationName(level: Int) = when (level) {
    Connection.TRANSACTION_READ_UNCOMMITTED -> "READ UNCOMMITTED"
    Connection.TRANSACTION_READ_COMMITTED -> "READ COMMITTED"
    Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE READ"
    Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE"
    else -> "nepoznato ($level)"
}

/** Svih 50 zahteva istovremeno; vraca broj prihvacenih. */
private fun naJednom(userIds: List<String>, zahtev: (String) -> Boolean): Int = runBlocking {
    userIds.map { uid -> async(Dispatchers.IO) { zahtev(uid) } }.awaitAll()
}.count { it }

fun main(args: Array<String>) {
    val izlaz = File(args.firstOrNull() ?: "build/negativni-prolaz").apply { mkdirs() }
    val baza = if (TestDatabase.isPostgres) "postgres" else "h2"

    TestDatabase.connect()
    val resourceId = UUID.randomUUID().toString()
    val (userIds, izolacija) = transaction {
        SchemaUtils.create(Users, Resources, ResourceAmenities, Bookings, BookingSlots, NaiveBookingSlots)
        val ids = (1..KORISNIKA).map { i ->
            UUID.randomUUID().toString().also { uid ->
                Users.insert {
                    it[id] = uid
                    it[name] = "Korisnik $i"
                    it[email] = "korisnik$i@firma.rs"
                    it[passwordHash] = "x"
                    it[role] = Role.USER.name
                }
            }
        }
        Resources.insert {
            it[id] = resourceId
            it[name] = "Sala Dunav"
            it[type] = ResourceType.MEETING_ROOM.name
            it[location] = "Sprat 1"
            it[capacity] = 8
            it[isActive] = true
        }
        ids to isolationName(connection.transactionIsolation)
    }

    val now = System.currentTimeMillis()
    val prviDan = BookingRules.dayStart(now + 24 * 3_600_000L)
    val slotaDnevno = BookingRules.slotsPerDay()
    var termin = 0

    /** Svaka runda trazi nov, jos nekoriscen termin od pola sata. */
    fun noviTermin(): CreateBookingRequest {
        val start = prviDan + (termin / slotaDnevno) * 86_400_000L + (termin % slotaDnevno) * BookingRules.SLOT_MILLIS
        termin++
        return CreateBookingRequest(resourceId, start, start + BookingRules.SLOT_MILLIS)
    }

    println("Baza: $baza | izolacija transakcija: $izolacija | $KORISNIKA istovremenih zahteva, $RUNDI rundi po varijanti")

    /**
     * Jedna runda: [prihvaceno] od 50 zahteva, uz broj stvarno izvrsenih transakcija.
     * Pokusaji sa indeksom bez barijere se ne broje: tu radi sam BookingService.
     */
    data class Red(
        val varijanta: String,
        val runda: Int,
        val bezIndeksa: Int,
        val pokusajaBezIndeksa: Int,
        val saIndeksom: Int,
        val pokusajaSaIndeksom: Int?
    )
    val redovi = mutableListOf<Red>()

    for (varijanta in listOf("bez barijere", "sa barijerom")) {
        val saBarijerom = varijanta == "sa barijerom"
        for (runda in 1..RUNDI) {
            val reqN = noviTermin()
            val bN = if (saBarijerom) CyclicBarrier(KORISNIKA) else null
            val pN = AtomicInteger()
            val bezIndeksa = naJednom(userIds) { uid -> naiveCreate(uid, reqN, now, Start(bN, pN)) }

            val reqI = noviTermin()
            val pI = AtomicInteger()
            val saIndeksom = if (saBarijerom) {
                val bI = CyclicBarrier(KORISNIKA)
                naJednom(userIds) { uid -> indexedCreate(uid, reqI, now, Start(bI, pI)) }
            } else {
                naJednom(userIds) { uid -> BookingService.create(uid, reqI, now) is BookingResult.Created }
            }
            val pokusajaSaIndeksom = if (saBarijerom) pI.get() else null

            println(
                "%-13s runda %2d: bez indeksa prihvaceno %2d (transakcija %d) | sa indeksom prihvaceno %d%s".format(
                    varijanta, runda, bezIndeksa, pN.get(), saIndeksom,
                    pokusajaSaIndeksom?.let { " (transakcija $it)" } ?: ""
                )
            )
            redovi += Red(varijanta, runda, bezIndeksa, pN.get(), saIndeksom, pokusajaSaIndeksom)
        }
    }

    for (varijanta in listOf("bez barijere", "sa barijerom")) {
        val r = redovi.filter { it.varijanta == varijanta }
        val n = r.map { it.bezIndeksa }.sorted()
        println(
            "--- %s, bez indeksa: min %d, medijana %d, max %d od %d; rundi sa duplom rezervacijom: %d/%d | sa indeksom uvek tacno 1: %s".format(
                varijanta, n.first(), n[n.size / 2], n.last(), KORISNIKA, n.count { it > 1 }, RUNDI,
                r.all { it.saIndeksom == 1 }
            )
        )
    }

    File(izlaz, "negativni-prolaz-$baza.csv").writeText(
        "baza,izolacija,varijanta,runda,bez_indeksa_prihvaceno,bez_indeksa_transakcija," +
            "sa_indeksom_prihvaceno,sa_indeksom_transakcija\n" +
            redovi.joinToString("\n") {
                "$baza,$izolacija,${it.varijanta},${it.runda},${it.bezIndeksa},${it.pokusajaBezIndeksa}," +
                    "${it.saIndeksom},${it.pokusajaSaIndeksom ?: ""}"
            } + "\n"
    )
}
