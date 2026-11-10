package rs.ftn.hotdesk.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.shared.model.BookingStatus
import rs.ftn.hotdesk.shared.model.ResourceDto

data class AdminResourcesState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Svi resursi, i neaktivni, po nazivu. */
    val resources: List<ResourceDto> = emptyList(),
    /** Resurs za koji je otvoren dijalog potvrde deaktivacije. */
    val confirming: ResourceDto? = null,
    /** Aktivne rezervacije nad resursom iz dijaloga; null dok se ne prebroje. */
    val activeBookings: Int? = null,
    val countFailed: Boolean = false,
    /** Resurs ciji se status upravo menja. */
    val changingId: String? = null,
    val notice: Notice? = null
)

/**
 * Upravljanje resursima (samo ADMIN): svi resursi, i neaktivni, uz deaktivaciju
 * i aktivaciju.
 *
 * Deaktivacija ne dira postojece rezervacije (ODLUKA u ResourceService.setActive na
 * serveru). Zato dijalog potvrde kaze koliko ih ostaje nad resursom: posledica
 * odluke treba da bude vidljiva onome ko deaktivira.
 */
class AdminResourcesViewModel : ViewModel() {

    private val _state = MutableStateFlow(AdminResourcesState())
    val state: StateFlow<AdminResourcesState> = _state.asStateFlow()

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
                // Server vraca listu vec poredjanu po nazivu.
                val svi = ApiClient.resources(activeOnly = false)
                _state.update { it.copy(isLoading = false, resources = svi) }
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

    /** Otvara dijalog potvrde i u pozadini broji aktivne rezervacije nad resursom. */
    fun askDeactivate(r: ResourceDto) {
        _state.update { it.copy(confirming = r, activeBookings = null, countFailed = false, notice = null) }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                // Isto pravilo kao "Aktivne" u Mojim rezervacijama: potvrdjena i jos nije zavrsena.
                val broj = ApiClient.bookingsForResource(r.id).count {
                    it.status == BookingStatus.CONFIRMED && it.endTime > now
                }
                // Dijalog je u medjuvremenu mogao biti zatvoren ili otvoren za drugi resurs.
                _state.update { if (it.confirming?.id == r.id) it.copy(activeBookings = broj) else it }
            } catch (e: ApiClient.SessionExpiredException) {
                // Session je vec zatvorena; MainActivity prelazi na prijavu.
            } catch (e: Exception) {
                _state.update { if (it.confirming?.id == r.id) it.copy(countFailed = true) else it }
            }
        }
    }

    fun dismissDeactivate() = _state.update { it.copy(confirming = null) }

    fun confirmDeactivate() {
        val r = _state.value.confirming ?: return
        _state.update { it.copy(confirming = null) }
        change(r, active = false)
    }

    /** Aktivacija je bezopasna i lako se ponistava, pa ide bez potvrde. */
    fun activate(r: ResourceDto) = change(r, active = true)

    private fun change(r: ResourceDto, active: Boolean) {
        _state.update { it.copy(changingId = r.id, notice = null) }

        viewModelScope.launch {
            val notice = try {
                val greska = ApiClient.setActive(r.id, active)
                when {
                    greska != null -> Notice(greska, isError = true)
                    active -> Notice("Aktiviran: ${r.name}. Ponovo se nudi za rezervacije.", isError = false)
                    else -> Notice("Deaktiviran: ${r.name}. Vise se ne nudi za nove rezervacije.", isError = false)
                }
            } catch (e: ApiClient.SessionExpiredException) {
                null
            } catch (e: Exception) {
                Notice(
                    "Server nije dostupan na ${ApiClient.baseUrl}. Promena nije izvrsena.",
                    isError = true
                )
            }
            _state.update { it.copy(changingId = null, notice = notice) }
            load()
        }
    }
}
