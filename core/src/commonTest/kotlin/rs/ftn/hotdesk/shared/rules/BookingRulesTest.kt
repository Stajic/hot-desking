package rs.ftn.hotdesk.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Testovi u commonTest se izvrsavaju na svakom targetu :shared modula.
 * Ista provera se dokazuje i za server i za Android, iz jednog fajla.
 */
class BookingRulesTest {

    // Utorak, 1. septembar 2026, 00:00 UTC
    private val dayUtcMidnight = 1_788_220_800_000L

    /** Pomocna: lokalni sat toga dana -> epoch millis. */
    private fun at(hour: Int, minute: Int = 0): Long =
        dayUtcMidnight +
            hour * 3_600_000L +
            minute * 60_000L -
            BookingRules.DEFAULT_ZONE_OFFSET_MILLIS

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
}
