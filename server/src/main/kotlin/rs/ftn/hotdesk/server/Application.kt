package rs.ftn.hotdesk.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import rs.ftn.hotdesk.server.db.DatabaseFactory
import rs.ftn.hotdesk.server.routes.apiRoutes
import rs.ftn.hotdesk.shared.model.ErrorResponse

/**
 * Ulazna tacka servera. Konfiguracija (port, baza) je u application.conf,
 * a engine se pokrece preko EngineMain - pokretanje je `./gradlew :server:run`.
 */
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(this)

    install(ContentNegotiation) {
        json(
            Json {
                // Klijent moze da bude stariji od servera; nova polja ga ne smeju rusiti.
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("BAD_REQUEST", cause.message ?: "Neispravan zahtev.")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Neobradjena greska", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL", "Doslo je do greske na serveru.")
            )
        }
    }

    routing {
        // Provera zivota - korisno kad se sa telefona proverava da li je server dostupan.
        get("/health") { call.respond(mapOf("status" to "ok")) }
        apiRoutes()
    }
}
