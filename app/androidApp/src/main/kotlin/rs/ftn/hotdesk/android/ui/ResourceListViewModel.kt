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
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType
import kotlin.coroutines.cancellation.CancellationException

/**
 * Jedno stanje, jedan izvor istine, jedan smer toka podataka (UDF).
 * UI cita [state] i salje namere nazad kroz javne metode - nikad obrnuto.
 */
data class ResourceListState(
    val isLoading: Boolean = true,
    val resources: List<ResourceDto> = emptyList(),
    /** Filter po tipu resursa. */
    val filter: ResourceType? = null,
    val location: String? = null,
    /** Izabrana oprema; resurs mora da ima svaku stavku. */
    val amenities: Set<String> = emptySet(),
    /** Vrednosti koje filteri nude, iz liste ucitane bez filtera. */
    val locationOptions: List<String> = emptyList(),
    val amenityOptions: List<String> = emptyList(),
    val error: String? = null
) {
    val hasFilters: Boolean
        get() = filter != null || location != null || amenities.isNotEmpty()
}

class ResourceListViewModel : ViewModel() {

    private val _state = MutableStateFlow(ResourceListState())
    val state: StateFlow<ResourceListState> = _state.asStateFlow()

    /**
     * Ucitavanje u toku. Nova promena filtera ga prekida: bez toga bi odgovor na
     * stariji izbor mogao da stigne posle novijeg i prikaze listu za pogresan filter.
     */
    private var loadJob: Job? = null

    init {
        load()
    }

    fun setFilter(type: ResourceType?) {
        _state.update { it.copy(filter = type) }
        load()
    }

    fun setLocation(location: String?) {
        _state.update { it.copy(location = location) }
        load()
    }

    fun toggleAmenity(amenity: String) {
        _state.update {
            it.copy(amenities = if (amenity in it.amenities) it.amenities - amenity else it.amenities + amenity)
        }
        load()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val s = _state.value
                // Filtrira server (?type, ?location, ?amenity); klijent samo salje izbor.
                val data = ApiClient.resources(type = s.filter, location = s.location, amenities = s.amenities)

                // Ponudjene lokacije i oprema prave se iz liste bez filtera, inace bi izbor
                // jedne lokacije sakrio sve ostale. Bez filtera to je upravo ova lista;
                // sa filterom se posebno ucitava samo ako ponuda jos ne postoji.
                val all = when {
                    !s.hasFilters -> data
                    s.locationOptions.isEmpty() -> ApiClient.resources()
                    else -> null
                }

                _state.update {
                    val loaded = it.copy(isLoading = false, resources = data)
                    if (all == null) loaded else loaded.copy(
                        locationOptions = all.map { r -> r.location }.distinct().sorted(),
                        amenityOptions = all.flatMap { r -> r.amenities }.distinct().sorted()
                    )
                }
            } catch (e: CancellationException) {
                // Prekinuto novijim izborom filtera - to nije greska.
                throw e
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
