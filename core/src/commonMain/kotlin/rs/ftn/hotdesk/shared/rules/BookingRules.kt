package rs.ftn.hotdesk.shared.rules

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import rs.ftn.hotdesk.shared.model.ResourceType
import kotlin.time.Instant

/**
 * Pravila rezervacije. Ovo je srce :shared modula.
 *
 * Ista funkcija radi na dva mesta sa dve razlicite svrhe:
 *  - na klijentu, da korisnik odmah vidi zasto dugme ne radi, bez odlaska na mrezu;
 *  - na serveru, kao autoritet - klijentu se nikad ne veruje.
 *
 * Nijedna metoda ne koristi `java.*` niti bilo koju platformsku biblioteku. To je
 * namerno: ako se u :shared doda non-JVM target (js/wasmJs), kompajler ce sam
 * proveravati da ovaj fajl ostaje platformski nezavisan. Rad sa vremenskom zonom
 * zato ide kroz kotlinx-datetime, koji je i sam multiplatformski.
 *
 * Od faze 2 radno vreme se racuna po stvarnoj vremenskoj zoni zgrade ([ZONE]),
 * ukljucujuci prelaz na letnje i zimsko racunanje vremena. Do tada je koriscen
 * fiksni pomeraj od UTC+2, zbog kog bi posle prelaska na zimsko vreme ceo radni
 * dan bio pomeren za sat.
 */
object BookingRules {

    /** Granularnost sistema. Sve rezervacije su celobrojni umnozak ove vrednosti. */
    const val SLOT_MINUTES = 30

    /** Radno vreme zgrade, lokalno. */
    const val DAY_START_HOUR = 8
    const val DAY_END_HOUR = 20

    /** 24 slota = 12h = ceo radni dan. Gornja granica za jednu rezervaciju. */
    const val MAX_SLOTS_PER_BOOKING = 24

    const val SLOT_MILLIS: Long = SLOT_MINUTES * 60_000L
    private const val HOUR_MILLIS: Long = 3_600_000L

    /**
     * Vremenska zona zgrade. Radno vreme 08-20 vazi po lokalnom satu, i leti i zimi,
     * nezavisno od toga u kojoj se zoni nalazi uredjaj ili server.
     */
    val ZONE: TimeZone = TimeZone.of("Europe/Belgrade")

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val code: String, val message: String) : Result
    }

    /**
     * Provera da li je trazeni interval uopste legitiman - nezavisno od toga
     * da li je slobodan. Zauzetost je pitanje baze, ne pravila.
     *
     * @param endTime ekskluzivan: rezervacija 09:00-10:00 zauzima slotove 09:00 i 09:30.
     */
    fun validate(
        startTime: Long,
        endTime: Long,
        now: Long,
        zone: TimeZone = ZONE
    ): Result {
        if (endTime <= startTime) {
            return Result.Invalid("RANGE", "Kraj rezervacije mora biti posle pocetka.")
        }
        if (startTime < now) {
            return Result.Invalid("PAST", "Nije moguce rezervisati termin u proslosti.")
        }
        // Poravnanje se proverava nad epoch vremenom. To je ispravno jer [ZONE] ima
        // pomeraj od celih sati (+1 / +2), pa je granica od 30 min ista u UTC i lokalno.
        if (startTime % SLOT_MILLIS != 0L || endTime % SLOT_MILLIS != 0L) {
            return Result.Invalid(
                "ALIGNMENT",
                "Vreme mora biti poravnato na $SLOT_MINUTES minuta."
            )
        }

        val slotCount = ((endTime - startTime) / SLOT_MILLIS).toInt()
        if (slotCount > MAX_SLOTS_PER_BOOKING) {
            return Result.Invalid(
                "TOO_LONG",
                "Jedna rezervacija moze da obuhvati najvise $MAX_SLOTS_PER_BOOKING slotova."
            )
        }

        val localStart = local(startTime, zone)
        // (endTime - 1) jer je kraj ekskluzivan: termin do tacno ponoci je jos uvek "danas",
        // a termin do tacno 20:00 jos uvek u radnom vremenu.
        val localLast = local(endTime - 1, zone)

        if (localStart.date != localLast.date) {
            return Result.Invalid("CROSSES_DAY", "Rezervacija ne sme da prelazi u naredni dan.")
        }

        if (localStart.time < LocalTime(DAY_START_HOUR, 0) || localLast.time >= LocalTime(DAY_END_HOUR, 0)) {
            return Result.Invalid(
                "OUTSIDE_HOURS",
                "Radno vreme je od $DAY_START_HOUR do $DAY_END_HOUR casova."
            )
        }

        return Result.Valid
    }

    /**
     * Razlaze interval na pocetke slotova koje zauzima.
     *
     * Ovo je funkcija koja povezuje DTO sa semom baze: rezervacija od 90 minuta
     * postaje tri reda u tabeli booking_slots, a jedinstveni indeks nad
     * (resource_id, slot_start) dalje garantuje da se ne mogu preklopiti.
     */
    fun slotStarts(startTime: Long, endTime: Long): List<Long> =
        generateSequence(startTime) { it + SLOT_MILLIS }
            .takeWhile { it < endTime }
            .toList()

    /**
     * Pocetak radnog dana (08:00 lokalno) za dati trenutak, kao epoch millis.
     *
     * Na dan prelaska na letnje ili zimsko vreme radni dan i dalje ima tacno
     * [slotsPerDay] slotova: sat se pomera u 02:00 / 03:00, van radnog vremena.
     */
    fun dayStart(
        anyTimeInDay: Long,
        zone: TimeZone = ZONE
    ): Long {
        val datum = local(anyTimeInDay, zone).date
        return LocalDateTime(datum, LocalTime(DAY_START_HOUR, 0))
            .toInstant(zone)
            .toEpochMilliseconds()
    }

    /** Broj slotova u radnom danu. */
    fun slotsPerDay(): Int =
        ((DAY_END_HOUR - DAY_START_HOUR) * HOUR_MILLIS / SLOT_MILLIS).toInt()

    /**
     * Podrazumevana duzina rezervacije po tipu resursa, u slotovima.
     * Model podataka je isti za sto i salu - razlikuje se samo ono sto UI nudi.
     */
    fun defaultSlots(type: ResourceType): Int = when (type) {
        ResourceType.DESK -> MAX_SLOTS_PER_BOOKING  // sto se uzima za ceo radni dan
        ResourceType.MEETING_ROOM -> 2              // sala podrazumevano jedan sat
    }

    private fun local(epochMillis: Long, zone: TimeZone): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
}
