package rs.ftn.hotdesk.shared.rules

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Testovi u commonTest se izvrsavaju na svakom targetu :shared modula.
 * Ista provera se dokazuje i za server i za Android, iz jednog fajla.
 */
class BookingRulesTest {

    /**
     * Zona u kojoj se zgrada nalazi. Namerno nije BookingRules.ZONE: test proverava
     * ponasanje prema stvarnom lokalnom satu u Beogradu, a ne prema konstanti koju
     * i sam testira.
     */
    private val beograd = TimeZone.of("Europe/Belgrade")

    /** Pomocna: lokalno vreme u Beogradu -> epoch millis. */
    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute)
            .toInstant(beograd)
            .toEpochMilliseconds()

    /**
     * Pomocna: lokalni sat u utorak, 1. septembra 2026 (letnje vreme, UTC+2).
     * Daje iste vrednosti kao ranija verzija sa fiksnim pomerajem, pa testovi
     * ispod zadrzavaju isto znacenje.
     */
    private fun at(hour: Int, minute: Int = 0): Long = local(2026, 9, 1, hour, minute)

    private val now = at(7)

    @Test
    fun prihvata_termin_u_radnom_vremenu() {
        val r = BookingRules.validate(at(9), at(10), now)
        assertEquals(BookingRules.Result.Valid, r)
    }

    @Test
    fun odbija_termin_u_proslosti() {
        val r = BookingRules.validate(at(9), at(10), now = at(11))
        assertIs<BookingRules.Result.Invalid>(r)
        assertEquals("PAST", r.code)
    }

    @Test
    fun odbija_neporavnat_pocetak() {
        val r = BookingRules.validate(at(9, 10), at(10), now)
        assertIs<BookingRules.Result.Invalid>(r)
        assertEquals("ALIGNMENT", r.code)
    }

    @Test
    fun odbija_termin_pre_radnog_vremena() {
        val r = BookingRules.validate(at(7), at(9), now = at(6))
        assertIs<BookingRules.Result.Invalid>(r)
        assertEquals("OUTSIDE_HOURS", r.code)
    }

    @Test
    fun odbija_termin_posle_radnog_vremena() {
        val r = BookingRules.validate(at(19, 30), at(20, 30), now)
        assertIs<BookingRules.Result.Invalid>(r)
        assertEquals("OUTSIDE_HOURS", r.code)
    }

    @Test
    fun prihvata_termin_koji_se_zavrsava_tacno_na_granici() {
        val r = BookingRules.validate(at(19, 30), at(20), now)
        assertEquals(BookingRules.Result.Valid, r)
    }

    @Test
    fun odbija_obrnut_interval() {
        val r = BookingRules.validate(at(11), at(10), now)
        assertIs<BookingRules.Result.Invalid>(r)
        assertEquals("RANGE", r.code)
    }

    @Test
    fun sat_i_po_daje_tri_slota() {
        val slots = BookingRules.slotStarts(at(9), at(10, 30))
        assertEquals(3, slots.size)
        assertEquals(at(9), slots[0])
        assertEquals(at(9, 30), slots[1])
        assertEquals(at(10), slots[2])
    }

    @Test
    fun kraj_je_ekskluzivan() {
        val slots = BookingRules.slotStarts(at(9), at(9, 30))
        assertEquals(1, slots.size)
        assertTrue(slots.none { it == at(9, 30) })
    }

    @Test
    fun radni_dan_ima_24_slota() {
        assertEquals(24, BookingRules.slotsPerDay())
    }

    // --- vremenska zona: dokaz da fiksni pomeraj vise ne vazi ---

    /**
     * Zimsko vreme (UTC+1). Stari kod sa fiksnim +2 bi 07:30 lokalno video kao
     * 08:30 i pustio termin pre pocetka radnog vremena.
     */
    @Test
    fun zimi_radno_vreme_pocinje_u_osam_po_lokalnom_satu() {
        val pre = local(2026, 11, 1, 6)
        assertEquals(
            BookingRules.Result.Valid,
            BookingRules.validate(local(2026, 11, 2, 8), local(2026, 11, 2, 9), now = pre)
        )
        val ranije = BookingRules.validate(local(2026, 11, 2, 7, 30), local(2026, 11, 2, 8, 30), now = pre)
        assertIs<BookingRules.Result.Invalid>(ranije)
        assertEquals("OUTSIDE_HOURS", ranije.code)
    }

    /** 08:00 lokalno je 06:00 UTC leti, a 07:00 UTC zimi. */
    @Test
    fun pocetak_dana_prati_letnje_i_zimsko_vreme() {
        val sat = 3_600_000L
        val leti = BookingRules.dayStart(local(2026, 9, 1, 12))
        val zimi = BookingRules.dayStart(local(2026, 11, 2, 12))
        assertEquals(6 * sat, leti % (24 * sat))
        assertEquals(7 * sat, zimi % (24 * sat))
    }

    /**
     * Dan prelaska na zimsko vreme (25. 10. 2026) ima 25 sati, ali se sat pomera
     * u 03:00 - radni dan i dalje pocinje u 08:00 i ima tacno 24 slota.
     */
    @Test
    fun dan_promene_vremena_ima_ispravan_radni_dan() {
        val pocetak = BookingRules.dayStart(local(2026, 10, 25, 12))
        assertEquals(local(2026, 10, 25, 8), pocetak)
        val krajDana = pocetak + BookingRules.slotsPerDay() * BookingRules.SLOT_MILLIS
        assertEquals(local(2026, 10, 25, 20), krajDana)
    }
}
