package rs.ftn.hotdesk.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.shared.model.AvailabilityDto
import kotlin.coroutines.cancellation.CancellationException

data class RoomInfoState(
    /** Poslednja dnevna mreza dobijena od servera; ostaje i kad veza pukne. */
    val availability: AvailabilityDto? = null,
    /** Vreme poslednjeg uspesnog osvezavanja. */
    val lastOk: Long? = null,
    /** Poslednji pokusaj nije uspeo; prikazuju se poslednji poznati podaci. */
    val offline: Boolean = false
)

/**
 * Dnevna mreza za jednu salu, osvezavana na [REFRESH_MILLIS].
 *
 * Status se ne racuna ovde nego na ekranu, iz mreze i trenutnog vremena koje se
 * menja svake sekunde: tako se "SLOBODNO" promeni u "ZAUZETO" tacno na granici
 * slota, a ne tek pri sledecem upitu serveru.
 *
 * Mreza se uvek trazi za trenutno vreme, pa ekran posle ponoci sam prelazi na novi dan.
 */
class RoomInfoViewModel(private val resourceId: String) : ViewModel() {

    private val _state = MutableStateFlow(RoomInfoState())
    val state: StateFlow<RoomInfoState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_MILLIS)
            }
        }
    }

    private suspend fun refresh() {
        try {
            val now = System.currentTimeMillis()
            val mreza = Server.api.availability(resourceId, now)
            _state.update { it.copy(availability = mreza, lastOk = now, offline = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ekran nastavlja da prikazuje poslednje poznato stanje, uz oznaku.
            _state.update { it.copy(offline = true) }
        }
    }

    private companion object {
        /**
         * 30 s: dovoljno sveze za ekran pored vrata, a zanemarljivo za server -
         * 20 ekrana je manje od jednog zahteva u sekundi.
         */
        const val REFRESH_MILLIS = 30_000L
    }
}
