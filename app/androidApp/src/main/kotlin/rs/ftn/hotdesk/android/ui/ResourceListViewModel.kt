package rs.ftn.hotdesk.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.android.data.ApiClient
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType

/**
 * Jedno stanje, jedan izvor istine, jedan smer toka podataka (UDF).
 * UI cita [state] i salje namere nazad kroz javne metode - nikad obrnuto.
 */
data class ResourceListState(
    val isLoading: Boolean = true,
    val resources: List<ResourceDto> = emptyList(),
    val filter: ResourceType? = null,
    val error: String? = null
)

class ResourceListViewModel : ViewModel() {

    private val _state = MutableStateFlow(ResourceListState())
    val state: StateFlow<ResourceListState> = _state.asStateFlow()

    init {
        load()
    }

    fun setFilter(type: ResourceType?) {
        _state.update { it.copy(filter = type) }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val data = ApiClient.resources(type = _state.value.filter)
                _state.update { it.copy(isLoading = false, resources = data) }
            } catch (e: Exception) {
                // Poruka govori sta da se uradi, ne samo sta je puklo.
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
}
