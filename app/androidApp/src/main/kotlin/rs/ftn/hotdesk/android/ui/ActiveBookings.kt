package rs.ftn.hotdesk.android.ui

import rs.ftn.hotdesk.shared.model.BookingDto
import rs.ftn.hotdesk.shared.model.BookingStatus

/*
 * Pravila prikaza rezervacija na klijentu, na jednom mestu.
 *
 * Koriste ih "Moje rezervacije", "Sve rezervacije" i brojanje rezervacija koje ostaju
 * nad resursom pri deaktivaciji. Da su zapisana na svakom od tih mesta, prva izmena
 * jednog bi ih razdvojila.
 */

/** Aktivna rezervacija: potvrdjena i jos nije zavrsena. Sve ostalo je istorija. */
fun BookingDto.isActive(now: Long): Boolean =
    status == BookingStatus.CONFIRMED && endTime > now

/** Otkazivanje ima smisla samo pre pocetka termina - i za korisnika i za administratora. */
fun BookingDto.canCancel(now: Long): Boolean =
    status == BookingStatus.CONFIRMED && startTime > now
