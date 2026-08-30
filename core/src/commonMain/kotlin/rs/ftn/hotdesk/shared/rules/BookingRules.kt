package rs.ftn.hotdesk.shared.rules

import rs.ftn.hotdesk.shared.model.ResourceType

/**
 * Pravila rezervacije. Ovo je srce :shared modula.
 *
 * Ista funkcija radi na dva mesta sa dve razlicite svrhe:
 *  - na klijentu, da korisnik odmah vidi zasto dugme ne radi, bez odlaska na mrezu;
 *  - na serveru, kao autoritet - klijentu se nikad ne veruje.
 *
 * Nijedna metoda ne koristi `java.*` niti bilo koju platformsku biblioteku. To je
 * namerno: ako se u :shared doda non-JVM target (js/wasmJs), kompajler ce sam
 * proveravati da ovaj fajl ostaje platformski nezavisan.
 *
 * OGRANICENJE (svesno, PoC faza): vremenska zona se racuna kao fiksni ofset od UTC.
 * DST prelaz (poslednja nedelja marta / oktobra) time nije pokriven. Faza 2 predviđa
 * zamenu bibliotekom kotlinx-datetime, cime se resava i to.
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
    private const val DAY_MILLIS: Long = 24 * HOUR_MILLIS

    /** Srbija, letnje racunanje vremena (UTC+2). Vidi ogranicenje u opisu klase. */
    const val DEFAULT_ZONE_OFFSET_MILLIS: Long = 2 * HOUR_MILLIS

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
        zoneOffsetMillis: Long = DEFAULT_ZONE_OFFSET_MILLIS
    ): Result {
        if (endTime <= startTime) {
            return Result.Invalid("RANGE", "Kraj rezervacije mora biti posle pocetka.")
        }
        if (startTime < now) {
            return Result.Invalid("PAST", "Nije moguce rezervisati termin u proslosti.")
        }
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

        val localStart = startTime + zoneOffsetMillis
        val localEnd = endTime + zoneOffsetMillis

        // (localEnd - 1) jer je kraj ekskluzivan: termin do tacno ponoci je jos uvek "danas".
        if (localStart / DAY_MILLIS != (localEnd - 1) / DAY_MILLIS) {
            return Result.Invalid("CROSSES_DAY", "Rezervacija ne sme da prelazi u naredni dan.")
        }

        val startOfDay = localStart % DAY_MILLIS
        val endOfDay = ((localEnd - 1) % DAY_MILLIS) + 1
        if (startOfDay < DAY_START_HOUR * HOUR_MILLIS || endOfDay > DAY_END_HOUR * HOUR_MILLIS) {
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

    /** Pocetak radnog dana (lokalno) za dati trenutak, kao epoch millis. */
    fun dayStart(
        anyTimeInDay: Long,
        zoneOffsetMillis: Long = DEFAULT_ZONE_OFFSET_MILLIS
    ): Long {
        val local = anyTimeInDay + zoneOffsetMillis
        val midnightLocal = (local / DAY_MILLIS) * DAY_MILLIS
        return midnightLocal + DAY_START_HOUR * HOUR_MILLIS - zoneOffsetMillis
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
}
