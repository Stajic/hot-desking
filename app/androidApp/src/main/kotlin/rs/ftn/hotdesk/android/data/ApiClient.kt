package rs.ftn.hotdesk.android.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import rs.ftn.hotdesk.shared.model.AvailabilityDto
import rs.ftn.hotdesk.shared.model.BookingDto
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.LoginRequest
import rs.ftn.hotdesk.shared.model.LoginResponse
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType

/**
 * Ktor Client. U fazi 1 zivi u :androidApp modulu.
 *
 * FAZA 2: preseliti u :shared/commonMain sa expect/actual HttpClientEngine-om.
 * Tek time deljeni modul pokriva i mrezni sloj, a ne samo model.
 */
object ApiClient {

    /**
     * VAZNO - ovo je najcesci uzrok "aplikacija ne moze da se poveze":
     *  - Android emulator vidi host masinu kao 10.0.2.2, NE kao localhost;
     *  - fizicki telefon zahteva IP adresu laptopa na lokalnoj mrezi
     *    (Windows: `ipconfig` -> IPv4 Address), i oba uredjaja na istom Wi-Fi-ju.
     *
     * Uz to, Android od verzije 9 blokira obican HTTP. Zato postoji
     * res/xml/network_security_config.xml.
     */
    var baseUrl: String = "http://10.0.2.2:8080"

    /**
     * JWT token dobijen prijavom. Postavlja ga i brise [Session]; ovde stoji zato
     * sto ga svaki zasticen zahtev salje kao `Authorization: Bearer <token>`.
     *
     * Dok je null, zasticene rute vracaju 401 - javne (lista resursa, dostupnost)
     * rade i bez njega.
     */
    var token: String? = null

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    /** Ishod prijave. Mrezni prekid se ne hvata ovde nego u ViewModel-u. */
    sealed interface LoginOutcome {
        data class Ok(val response: LoginResponse) : LoginOutcome
        data object BadCredentials : LoginOutcome
        data class Failed(val message: String) : LoginOutcome
    }

    /**
     * Prijava. Server namerno vraca istu poruku za nepostojeci email i pogresnu
     * lozinku, pa ih ni klijent ne razdvaja.
     */
    suspend fun login(email: String, password: String): LoginOutcome {
        val response = client.post("$baseUrl/api/auth/login") {
            setBody(LoginRequest(email = email, password = password))
        }
        return when (response.status) {
            HttpStatusCode.OK -> LoginOutcome.Ok(response.body())
            HttpStatusCode.Unauthorized -> LoginOutcome.BadCredentials
            else -> LoginOutcome.Failed("Neocekivan odgovor servera (${response.status.value}).")
        }
    }

    suspend fun resources(
        type: ResourceType? = null,
        location: String? = null
    ): List<ResourceDto> =
        client.get("$baseUrl/api/resources") {
            type?.let { parameter("type", it.name) }
            location?.let { parameter("location", it) }
        }.body()

    suspend fun availability(resourceId: String, at: Long): AvailabilityDto =
        client.get("$baseUrl/api/resources/$resourceId/availability") {
            parameter("at", at)
        }.body()

    suspend fun myBookings(): List<BookingDto> =
        client.get("$baseUrl/api/bookings/mine") {
            token?.let { bearerAuth(it) }
        }.body()

    sealed interface BookingOutcome {
        data class Ok(val booking: BookingDto) : BookingOutcome
        data object Taken : BookingOutcome
        data class Rejected(val message: String) : BookingOutcome
    }

    suspend fun book(req: CreateBookingRequest): BookingOutcome {
        val response = client.post("$baseUrl/api/bookings") {
            token?.let { bearerAuth(it) }
            setBody(req)
        }
        return when (response.status) {
            HttpStatusCode.Created -> BookingOutcome.Ok(response.body())
            // 409 dolazi od jedinstvenog indeksa u bazi - neko je bio brzi.
            HttpStatusCode.Conflict -> BookingOutcome.Taken
            else -> BookingOutcome.Rejected("Zahtev nije prihvacen (${response.status.value}).")
        }
    }
}
