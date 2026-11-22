package rs.ftn.hotdesk.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType

data class RoomPickerState(
    val isLoading: Boolean = true,
    val rooms: List<ResourceDto> = emptyList(),
    val error: String? = null
)

/** Aktivne sale sa servera, za izbor sale koju ekran prikazuje. */
class RoomPickerViewModel : ViewModel() {

    private val _state = MutableStateFlow(RoomPickerState())
    val state: StateFlow<RoomPickerState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Ekran stoji pored sale za sastanke; stolovi se ne nude.
                val sale = Server.api.resources(type = ResourceType.MEETING_ROOM)
                _state.update { it.copy(isLoading = false, rooms = sale) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Server nije dostupan na ${Server.baseUrl}."
                    )
                }
            }
        }
    }
}
