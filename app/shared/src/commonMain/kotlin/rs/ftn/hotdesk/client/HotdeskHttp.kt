package rs.ftn.hotdesk.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * HTTP klijent za server, isti za svaki klijent sistema: telefon i ekran na STB uredjaju.
 *
 * Podesavanja su na jednom mestu: JSON preko kotlinx.serialization (iste DTO klase iz
 * :core koje server serijalizuje), ogranicenja vremena i JSON kao podrazumevani tip
 * tela zahteva. Platformski je samo engine, preko expect/actual.
 */
fun hotdeskHttpClient(): HttpClient = HttpClient(platformEngine()) {
    install(ContentNegotiation) {
        // Server moze biti noviji od klijenta; nova polja ga ne smeju rusiti.
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

/** HTTP engine platforme. Na Androidu OkHttp. */
internal expect fun platformEngine(): HttpClientEngineFactory<*>
