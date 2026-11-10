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
import rs.ftn.hotdesk.shared.model.UpsertResourceRequest
import rs.ftn.hotdesk.shared.rules.ResourceRules

/** Polja forme cuvaju tekst onako kako je otkucan; u zahtev se pretvaraju tek pri cuvanju. */
data class ResourceFormState(
    val name: String = "",
    val type: ResourceType = ResourceType.DESK,
    val location: String = "",
    val capacity: String = "1",
    /** Oprema odvojena zarezima, npr. "projektor, tabla". */
    val amenities: String = "",
    /** Kod polja sa greskom (NAME, LOCATION, CAPACITY, AMENITIES), iz ResourceRules. */
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    /** Greska koja nije vezana za polje: server nedostupan, resurs vise ne postoji. */
    val notice: Notice? = null,
    /** Naziv sacuvanog resursa; ekran tada zatvara formu. */
    val savedName: String? = null
)

/**
 * Forma za dodavanje i izmenu resursa (samo ADMIN).
 *
 * Pre slanja forma proverava zahtev sa ResourceRules iz modula :core - istom
 * funkcijom koju server izvrsava pre upisa. Neispravan zahtev se zato ni ne salje,
 * a greska stoji ispod polja koje kod imenuje. Ako ga server ipak odbije, njegov kod
 * je isti, pa se greska prikazuje na istom mestu.
 */
class ResourceFormViewModel : ViewModel() {

    private val _state = MutableStateFlow(ResourceFormState())
    val state: StateFlow<ResourceFormState> = _state.asStateFlow()

    /** Resurs koji se menja; null za nov resurs. */
    private var editing: ResourceDto? = null

    /**
     * Pri svakom ulasku forma krece od vrednosti koje je server poslednji vratio,
     * a ne od izmena ostavljenih pri prethodnom otvaranju.
     */
    fun onEnter(resource: ResourceDto?) {
        editing = resource
        _state.value = if (resource == null) {
            ResourceFormState()
        } else {
            ResourceFormState(
                name = resource.name,
                type = resource.type,
                location = resource.location,
                capacity = resource.capacity.toString(),
                amenities = resource.amenities.joinToString(", ")
            )
        }
    }

    fun setName(v: String) = edit("NAME") { it.copy(name = v) }

    fun setLocation(v: String) = edit("LOCATION") { it.copy(location = v) }

    fun setCapacity(v: String) = edit("CAPACITY") { it.copy(capacity = v) }

    fun setAmenities(v: String) = edit("AMENITIES") { it.copy(amenities = v) }

    /** Sto je uvek za jednu osobu; kod prelaska na salu kapacitet se upisuje iznova. */
    fun setType(t: ResourceType) = edit("CAPACITY") {
        it.copy(
            type = t,
            capacity = when {
                t == ResourceType.DESK -> "1"
                it.type == ResourceType.DESK -> ""
                else -> it.capacity
            }
        )
    }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        val req = request(s)

        // Ista pravila koja server primenjuje pre upisa; neispravan zahtev se ni ne salje.
        val provera = ResourceRules.validate(req)
        if (provera is ResourceRules.Result.Invalid) {
            _state.update { it.copy(errorCode = provera.code, errorMessage = provera.message, notice = null) }
            return
        }

        _state.update { it.copy(isSaving = true, errorCode = null, errorMessage = null, notice = null) }
        viewModelScope.launch {
            try {
                val r = editing
                val ishod = if (r == null) ApiClient.createResource(req) else ApiClient.updateResource(r.id, req)
                _state.update {
                    when (ishod) {
                        is ApiClient.SaveOutcome.Saved ->
                            it.copy(isSaving = false, savedName = ishod.resource.name)

                        // Server je autoritet. Kod polja je isti kao kod provere na klijentu,
                        // pa greska staje na isto mesto.
                        is ApiClient.SaveOutcome.Invalid ->
                            if (ishod.code in FIELDS) {
                                it.copy(isSaving = false, errorCode = ishod.code, errorMessage = ishod.message)
                            } else {
                                it.copy(isSaving = false, notice = Notice(ishod.message, isError = true))
                            }

                        ApiClient.SaveOutcome.NotFound ->
                            it.copy(isSaving = false, notice = Notice("Resurs vise ne postoji.", isError = true))

                        is ApiClient.SaveOutcome.Failed ->
                            it.copy(isSaving = false, notice = Notice(ishod.message, isError = true))
                    }
                }
            } catch (e: ApiClient.SessionExpiredException) {
                // Session je vec zatvorena; MainActivity prelazi na prijavu.
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        notice = Notice(
                            "Server nije dostupan na ${ApiClient.baseUrl}. Resurs nije sacuvan.",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    /** Ekran je preuzeo [ResourceFormState.savedName] i zatvara formu. */
    fun savedHandled() = _state.update { it.copy(savedName = null) }

    /** Izmena polja; greska tog polja nestaje cim administrator pocne da ga ispravlja. */
    private fun edit(field: String, change: (ResourceFormState) -> ResourceFormState) =
        _state.update { s ->
            val novo = change(s)
            if (novo.errorCode == field) novo.copy(errorCode = null, errorMessage = null) else novo
        }

    private fun request(s: ResourceFormState) = UpsertResourceRequest(
        name = s.name.trim(),
        type = s.type,
        location = s.location.trim(),
        // Prazno polje ili nebroj postaje 0, pa ga odbija pravilo za kapacitet.
        capacity = s.capacity.trim().toIntOrNull() ?: 0,
        // Prazne stavke posle zareza ("projektor, ") se odbacuju.
        amenities = s.amenities.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        // Aktivnost se ne menja kroz formu: izmena zadrzava postojecu, nov resurs je aktivan.
        isActive = editing?.isActive ?: true
    )

    private companion object {
        val FIELDS = setOf("NAME", "LOCATION", "CAPACITY", "AMENITIES")
    }
}
