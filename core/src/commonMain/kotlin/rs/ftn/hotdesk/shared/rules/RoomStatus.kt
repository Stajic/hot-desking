package rs.ftn.hotdesk.shared.rules

import rs.ftn.hotdesk.shared.model.AvailabilityDto
import rs.ftn.hotdesk.shared.model.SlotDto

/** Vremenski interval [start, end), u epoch milisekundama. */
data class TimeRange(val start: Long, val end: Long)

/**
 * Status sale u jednom trenutku, izracunat iz dnevne mreze slotova.
 *
 * Koristi ga ekran pored sale (STB, faza 5): "SLOBODNO", "ZAUZETO do 15:00",
 * "Sledece: 15:30-16:30". Racun je cista funkcija nad AvailabilityDto, pa je u :core:
 * isti za svaki klijent koji ga prikazuje, i testiran jednom, kao BookingRules.
 *
 * Uzastopni zauzeti slotovi spajaju se u jedan blok i kad pripadaju razlicitim
 * rezervacijama: za onoga ko stoji ispred sale, ona je zauzeta do kraja bloka.
 *
 * Granice se citaju iz samih slotova koje salje server, a ne iz konstanti
 * BookingRules, pa racun vazi i na dan promene vremena.
 */
sealed interface RoomStatus {

    /** Pre pocetka ili posle kraja radnog dana iz mreze. */
    data object OutsideHours : RoomStatus

    /** Slobodna do [until]: pocetka sledeceg zauzeca [next], ili kraja radnog dana. */
    data class Free(val until: Long, val next: TimeRange?) : RoomStatus

    /** Zauzeta do [until], kraja tekuceg bloka; [next] je sledeci blok posle pauze. */
    data class Occupied(val until: Long, val next: TimeRange?) : RoomStatus

    companion object {

        fun of(availability: AvailabilityDto, now: Long): RoomStatus {
            val slots = availability.slots
            if (slots.isEmpty()) return OutsideHours

            val i = slots.indexOfFirst { now >= it.startTime && now < it.endTime }
            if (i < 0) return OutsideHours

            return if (slots[i].isAvailable) {
                val next = takenBlock(slots, from = i)
                Free(until = next?.start ?: slots.last().endTime, next = next)
            } else {
                val until = takenBlock(slots, from = i)!!.end
                val posle = slots.indexOfFirst { it.startTime >= until }
                Occupied(until = until, next = if (posle < 0) null else takenBlock(slots, from = posle))
            }
        }

        /** Prvi blok uzastopnih zauzetih slotova, pocev od indeksa [from]; null ako ga nema. */
        private fun takenBlock(slots: List<SlotDto>, from: Int): TimeRange? {
            val start = (from until slots.size).firstOrNull { !slots[it].isAvailable } ?: return null
            var end = start
            while (end + 1 < slots.size && !slots[end + 1].isAvailable) end++
            return TimeRange(slots[start].startTime, slots[end].endTime)
        }
    }
}
