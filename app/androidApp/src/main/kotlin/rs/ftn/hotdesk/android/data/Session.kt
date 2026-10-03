package rs.ftn.hotdesk.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.ftn.hotdesk.shared.model.LoginResponse
import rs.ftn.hotdesk.shared.model.UserDto

/**
 * Prijavljeni korisnik i njegov token.
 *
 * Jedini izvor istine o tome da li je korisnik prijavljen. [user] posmatra
 * MainActivity da bi birao izmedju ekrana za prijavu i liste resursa.
 *
 * ODLUKA: sesija zivi samo u memoriji procesa. Posledica je da se prijava trazi
 * pri svakom pokretanju aplikacije, i da gasenje procesa u pozadini odjavljuje
 * korisnika. Alternativa je trajno cuvanje (DataStore), ali bi token tada lezao
 * u skladistu uredjaja i zahtevao odgovor na pitanje kako se stiti - a token
 * ionako vazi 24 sata, pa bi trajno cuvanje resilo malo a otvorilo mnogo.
 * Za fazu 2 to nije potrebno.
 *
 * [UserDto] i [LoginResponse] dolaze iz modula :core - iste klase koje server
 * serijalizuje. Nijedna nije ponovo napisana na klijentu.
 */
object Session {

    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    /** Uspesna prijava: token ide u [ApiClient], korisnik u stanje koje UI posmatra. */
    fun start(response: LoginResponse) {
        ApiClient.token = response.token
        _user.value = response.user
    }

    fun end() {
        ApiClient.token = null
        _user.value = null
    }
}
