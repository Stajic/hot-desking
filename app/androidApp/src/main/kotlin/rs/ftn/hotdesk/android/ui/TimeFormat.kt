package rs.ftn.hotdesk.android.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import rs.ftn.hotdesk.shared.rules.BookingRules
import kotlin.time.Instant

/**
 * Jedino mesto na klijentu koje pretvara trenutak (epoch millis) u vreme za prikaz.
 *
 * Koristi istu vremensku zonu kao BookingRules iz modula :core ([BookingRules.ZONE]),
 * pa klijent prikazuje tacno one slotove koje server racuna - i leti i zimi, i na
 * dan prelaska na letnje ili zimsko vreme.
 *
 * kotlinx-datetime nema lokalizovano formatiranje, pa su nazivi dana napisani ovde.
 * Zauzvrat klijent vise ne koristi ni java.text ni java.util.
 */
object TimeFormat {

    private val dani = mapOf(
        DayOfWeek.MONDAY to "ponedeljak",
        DayOfWeek.TUESDAY to "utorak",
        DayOfWeek.WEDNESDAY to "sreda",
        DayOfWeek.THURSDAY to "cetvrtak",
        DayOfWeek.FRIDAY to "petak",
        DayOfWeek.SATURDAY to "subota",
        DayOfWeek.SUNDAY to "nedelja"
    )

    /** "09:30" */
    fun hhmm(epochMillis: Long): String {
        val t = local(epochMillis).time
        return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }

    /** "ponedeljak, 5. 10. 2026." */
    fun day(epochMillis: Long): String {
        val d = local(epochMillis).date
        return "${dani.getValue(d.dayOfWeek)}, ${d.day}. ${d.month.number}. ${d.year}."
    }

    private fun local(epochMillis: Long): LocalDateTime =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(BookingRules.ZONE)
}
