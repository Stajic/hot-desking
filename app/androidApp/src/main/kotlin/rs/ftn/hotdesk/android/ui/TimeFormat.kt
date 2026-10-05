package rs.ftn.hotdesk.android.ui

import rs.ftn.hotdesk.shared.rules.BookingRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone

/**
 * Jedino mesto na klijentu koje pretvara trenutak (epoch millis) u vreme za prikaz.
 *
 * Koristi ISTI fiksni pomeraj od UTC kao BookingRules iz modula :core, da bi klijent
 * prikazivao tacno one slotove koje server racuna. Posledica je poznato ogranicenje
 * iz README-a: prelaz na zimsko racunanje vremena nije pokriven, pa posle njega
 * prikaz odstupa za sat od zidnog sata.
 *
 * Prelazak na kotlinx-datetime (korak 4) na klijentu menja samo ovaj fajl.
 *
 * java.time se namerno ne koristi: minSdk je 24, a java.time je na Androidu
 * dostupan tek od API 26 bez dodatnog desugaring-a.
 */
object TimeFormat {

    private const val MINUTE = 60_000L
    private const val DAY = 86_400_000L

    private val zone = SimpleTimeZone(BookingRules.DEFAULT_ZONE_OFFSET_MILLIS.toInt(), "BookingRules")

    /** "09:30" */
    fun hhmm(epochMillis: Long): String {
        val local = epochMillis + BookingRules.DEFAULT_ZONE_OFFSET_MILLIS
        val minutesOfDay = ((local % DAY + DAY) % DAY) / MINUTE
        return "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
    }

    /** "ponedeljak, 5. 10. 2026." */
    fun day(epochMillis: Long): String =
        SimpleDateFormat("EEEE, d. M. yyyy.", Locale.forLanguageTag("sr-Latn-RS"))
            .apply { timeZone = zone }
            .format(Date(epochMillis))
}
