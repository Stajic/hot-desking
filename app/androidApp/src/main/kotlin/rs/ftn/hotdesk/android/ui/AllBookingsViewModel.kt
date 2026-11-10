package rs.ftn.hotdesk.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.shared.model.BookingDto
import kotlin.coroutines.cancellation.CancellationException

/** Ponudjena vrednost filtera: id za server, naziv za prikaz. */
data class FilterOption(val id: String, val label: String)

data class AllBookingsState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Potvrdjene, koje se jos nisu zavrsile - najblize prve. */
    val active: List<BookingDto> = emptyList(),
    /** Otkazane i zavrsene - najskorije prve. */
    val history: List<BookingDto> = emptyList(),
    val userId: String? = null,
    val resourceId: String? = null,
    /** Korisnici i resursi koji imaju rezervacije, iz liste ucitane bez filtera. */
    val userOptions: List<FilterOption> = emptyList(),
    val resourceOptions: List<FilterOption> = emptyList(),
    /** Rezervacija za koju je otvoren dijalog potvrde otkazivanja. */
    val confirming: BookingDto? = null,
    val cancellingId: String? = null,
    val notice: Notice? = null
) {
    val hasFilters: Boolean get() = userId != null || resourceId != null
}

/**
 * "Sve rezervacije" (samo ADMIN): globalni pregled sa filterima po korisniku i
 * resursu, uz otkazivanje bilo koje aktivne rezervacije.
 *
 * Filtrira server (?userId, ?resourceId). Ponudjeni korisnici se prave iz samih
 * rezervacija, koje vec nose ime korisnika, pa za njih nije potrebna posebna ruta.
 */
class AllBookingsViewModel : ViewModel() {

    private val _state = MutableStateFlow(AllBookingsState())
    val state: StateFlow<AllBookingsState> = _state.asStateFlow()

    /** Kao na listi resursa: novi izbor filtera prekida ucitavanje za stariji. */
    private var loadJob: Job? = null

    /**
     * @param resourceId filter po resursu pri ulasku, kad se pregled otvara iz
     *        dijaloga deaktivacije; inace null.
     */
    fun onEnter(resourceId: String?) {
        // Ponuda filtera se pravi iznova: u medjuvremenu su mogli da rezervisu novi korisnici.
        _state.update {
            it.copy(
                userId = null,
                resourceId = resourceId,
                userOptions = emptyList(),
                resourceOptions = emptyList(),
                notice = null,
                confirming = null
            )
        }
        load()
    }

    fun setUser(id: String?) {
        _state.update { it.copy(userId = id) }
        load()
    }

    fun setResource(id: String?) {
        _state.update { it.copy(resourceId = id) }
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val s = _state.value
                val data = ApiClient.allBookings(userId = s.userId, resourceId = s.resourceId)

                // Ponuda filtera iz liste bez filtera, kao na listi resursa: izbor jednog
                // korisnika ne sme da sakrije ostale.
                val all = when {
                    !s.hasFilters -> data
                    s.userOptions.isEmpty() -> ApiClient.allBookings()
                    else -> null
                }

                val now = System.currentTimeMillis()
                val (aktivne, ostale) = data.partition { it.isActive(now) }
                _state.update {
                    val loaded = it.copy(
                        isLoading = false,
                        active = aktivne.sortedBy { b -> b.startTime },
                        history = ostale.sortedByDescending { b -> b.startTime }
                    )
                    if (all == null) loaded else loaded.copy(
                        userOptions = all.map { b -> FilterOption(b.userId, b.userName) }
                            .distinct().sortedBy { o -> o.label },
                        resourceOptions = all.map { b -> FilterOption(b.resourceId, b.resourceName) }
                            .distinct().sortedBy { o -> o.label }
                    )
                }
            } catch (e: CancellationException) {
                throw e
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

    fun canCancel(b: BookingDto): Boolean = b.canCancel(System.currentTimeMillis())

    fun askCancel(b: BookingDto) = _state.update { it.copy(confirming = b, notice = null) }

    fun dismissCancel() = _state.update { it.copy(confirming = null) }

    fun confirmCancel() {
        val b = _state.value.confirming ?: return
        _state.update { it.copy(confirming = null, cancellingId = b.id) }

        viewModelScope.launch {
            val opis = "${b.resourceName}, ${b.userName}, " +
                "${TimeFormat.hhmm(b.startTime)}-${TimeFormat.hhmm(b.endTime)}"
            val notice = try {
                // Server administratoru dozvoljava da otkaze tudju rezervaciju (BookingService.cancel).
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
