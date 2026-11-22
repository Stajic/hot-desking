package rs.ftn.hotdesk.shared.rules

import rs.ftn.hotdesk.shared.model.AvailabilityDto
import rs.ftn.hotdesk.shared.model.SlotDto
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Status sale za ekran pored nje. Mreza je izmisljen dan od 24 slota po pola sata,
 * od 08:00; slot n pocinje u 08:00 + n * 30 min.
 */
class RoomStatusTest {

    private val dayStart = 1_000_000_000_000L - 1_000_000_000_000L % BookingRules.SLOT_MILLIS
    private val slot = BookingRules.SLOT_MILLIS

    /** Pocetak slota [n]. */
    private fun t(n: Int): Long = dayStart + n * slot

    /** Mreza dana u kojoj su zauzeti slotovi [zauzeti]. */
    private fun mreza(vararg zauzeti: Int) = AvailabilityDto(
        resourceId = "sala",
        dayStart = dayStart,
        slots = (0 until 24).map { n -> SlotDto(t(n), t(n + 1), isAvailable = n !in zauzeti) }
    )

    @Test
    fun pre_i_posle_radnog_vremena() {
        assertEquals(RoomStatus.OutsideHours, RoomStatus.of(mreza(), now = t(0) - 1))
        // Kraj je ekskluzivan: tacno u 20:00 radni dan je zavrsen.
        assertEquals(RoomStatus.OutsideHours, RoomStatus.of(mreza(), now = t(24)))
    }

    @Test
    fun slobodna_ceo_dan() {
        assertEquals(RoomStatus.Free(until = t(24), next = null), RoomStatus.of(mreza(), now = t(3)))
    }

    @Test
    fun slobodna_do_sledeceg_zauzeca() {
        // zauzeto 11:00-12:00, sada 09:15
        val s = RoomStatus.of(mreza(6, 7), now = t(2) + slot / 2)
        assertEquals(RoomStatus.Free(until = t(6), next = TimeRange(t(6), t(8))), s)
    }

    @Test
    fun uzastopni_slotovi_su_jedan_blok() {
        // zauzeto 09:00-10:30 (tri slota, mogu biti i razlicite rezervacije), sada usred
        val s = RoomStatus.of(mreza(2, 3, 4), now = t(3) + 1)
        assertEquals(RoomStatus.Occupied(until = t(5), next = null), s)
    }

    @Test
    fun zauzeta_pa_sledece_posle_pauze() {
        // zauzeto 09:00-10:00, slobodno 10:00-11:00, zauzeto 11:00-11:30
        val s = RoomStatus.of(mreza(2, 3, 6), now = t(2))
        assertEquals(RoomStatus.Occupied(until = t(4), next = TimeRange(t(6), t(7))), s)
    }

    @Test
    fun granice_slota() {
        val m = mreza(4)
        // tacno na pocetku zauzetog slota: zauzeta
        assertEquals(RoomStatus.Occupied(until = t(5), next = null), RoomStatus.of(m, now = t(4)))
        // tacno na njegovom kraju: ponovo slobodna, do kraja dana
        assertEquals(RoomStatus.Free(until = t(24), next = null), RoomStatus.of(m, now = t(5)))
    }

    @Test
    fun poslednji_slot_dana() {
        assertEquals(RoomStatus.Occupied(until = t(24), next = null), RoomStatus.of(mreza(23), now = t(23)))
    }

    @Test
    fun prazna_mreza_je_van_radnog_vremena() {
        val prazna = AvailabilityDto(resourceId = "sala", dayStart = dayStart, slots = emptyList())
        assertEquals(RoomStatus.OutsideHours, RoomStatus.of(prazna, now = t(3)))
    }
}
