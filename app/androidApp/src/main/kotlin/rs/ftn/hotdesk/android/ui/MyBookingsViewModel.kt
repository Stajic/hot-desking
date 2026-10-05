package rs.ftn.hotdesk.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.shared.model.BookingDto
import rs.ftn.hotdesk.shared.model.BookingStatus

data class MyBookingsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Potvrdjene, koje se jos nisu zavrsile - najblize prve. */
    val active: List<BookingDto> = emptyList(),
    /** Otkazane i zavrsene - najskorije prve. */
    val history: List<BookingDto> = emptyList(),
    /** Rezervacija za koju je otvoren dijalog potvrde otkazivanja. */
    val confirming: BookingDto? = null,
    val cancellingId: String? = null,
    val notice: Notice? = null
)

/**
 * "Moje rezervacije": pregled aktivnih i isteklih, uz otkazivanje.
 *
 * Otkazivanje na serveru brise slotove - termin se odmah oslobadja za druge - ali
 * zaglavlje ostaje sa statusom CANCELLED. Zato otkazana rezervacija ne nestaje sa
 * ovog ekrana, nego prelazi u istoriju.
 */
class MyBookingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(MyBookingsState())
    val state: StateFlow<MyBookingsState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEnter() {
        _state.update { it.copy(notice = null, confirming = null) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val sve = ApiClient.myBookings()
                val now = System.currentTimeMillis()
                val (aktivne, ostale) = sve.partition {
                    it.status == BookingStatus.CONFIRMED && it.endTime > now
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        active = aktivne.sortedBy { b -> b.startTime },
                        history = ostale.sortedByDescending { b -> b.startTime }
                    )
                }
            } catch (e: ApiClient.SessionExpiredException) {
                // Session je vec zatvorena; MainActivity prelazi na prijavu.
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Server nije dostupan na ${ApiClient.baseUrl}. " +
                            "Proveri da li je pokrenut i da li adresa odgovara uredjaju."
                    )
                }
            }
        }
    }

    /** Otkazivanje ima smisla samo pre pocetka termina. */
    fun canCancel(b: BookingDto): Boolean =
        b.status == BookingStatus.CONFIRMED && b.startTime > System.currentTimeMillis()

    fun askCancel(b: BookingDto) = _state.update { it.copy(confirming = b, notice = null) }

    fun dismissCancel() = _state.update { it.copy(confirming = null) }

    fun confirmCancel() {
        val b = _state.value.confirming ?: return
        _state.update { it.copy(confirming = null, cancellingId = b.id) }

        viewModelScope.launch {
            val opis = "${b.resourceName}, ${TimeFormat.hhmm(b.startTime)}-${TimeFormat.hhmm(b.endTime)}"
            val notice = try {
                if (ApiClient.cancel(b.id)) {
                    Notice("Otkazano: $opis. Termin je ponovo slobodan.", isError = false)
                } else {
                    Notice("Rezervacija vise ne postoji.", isError = true)
                }
            } catch (e: ApiClient.SessionExpiredException) {
                null
            } catch (e: Exception) {
                Notice(
                    "Server nije dostupan na ${ApiClient.baseUrl}. Otkazivanje nije izvrseno.",
                    isError = true
                )
            }
            _state.update { it.copy(cancellingId = null, notice = notice) }
            load()
        }
    }
}
