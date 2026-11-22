package rs.ftn.hotdesk.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import rs.ftn.hotdesk.shared.model.AvailabilityDto
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType

/**
 * Javne rute servera: lista resursa i dnevna mreza slotova. Ne traze token.
 *
 * Koriste ih aplikacija na telefonu (lista resursa, mreza slotova) i ekran na STB
 * uredjaju. Rute koje traze prijavu ostaju u klijentu telefona, jer zavise od
 * njegove sesije.
 *
 * @param baseUrl adresa servera; funkcija, jer je klijent postavlja pri pokretanju.
 */
class PublicApi(private val client: HttpClient, private val baseUrl: () -> String) {

    suspend fun resources(
        type: ResourceType? = null,
        location: String? = null,
        amenities: Collection<String> = emptyList(),
        activeOnly: Boolean = true
    ): List<ResourceDto> =
        client.get("${baseUrl()}/api/resources") {
            type?.let { parameter("type", it.name) }
            location?.let { parameter("location", it) }
            // Svaka stavka je zaseban parametar (?amenity=a&amenity=b); server vraca
            // resurse koji imaju sve navedene.
            amenities.forEach { parameter("amenity", it) }
            // Server podrazumeva samo aktivne; neaktivne trazi upravljanje resursima.
            if (!activeOnly) parameter("activeOnly", false)
        }.body()

    suspend fun availability(resourceId: String, at: Long): AvailabilityDto =
        client.get("${baseUrl()}/api/resources/$resourceId/availability") {
            parameter("at", at)
        }.body()
}
