package rs.ftn.hotdesk.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import rs.ftn.hotdesk.server.auth.JwtService
import rs.ftn.hotdesk.server.auth.UserPrincipal
import rs.ftn.hotdesk.server.db.DatabaseFactory
import rs.ftn.hotdesk.server.routes.apiRoutes
import rs.ftn.hotdesk.server.routes.authRoutes
import rs.ftn.hotdesk.shared.model.ErrorResponse
import rs.ftn.hotdesk.shared.model.Role

/**
 * Ulazna tacka servera. Konfiguracija (port, baza) je u application.conf,
 * a engine se pokrece preko EngineMain - pokretanje je `./gradlew :server:run`.
 */
fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(this)

    val jwtService = JwtService(environment.config)

    install(ContentNegotiation) {
        json(
            Json {
                // Klijent moze da bude stariji od servera; nova polja ga ne smeju rusiti.
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(Authentication) {
        jwt(JwtService.CONFIG_NAME) {
            realm = jwtService.realm
            verifier(jwtService.verifier)

            // Token je vec kriptografski proveren; ovde se iz njega vadi identitet.
            validate { credential ->
                val userId = credential.payload.subject
                val role = credential.payload.getClaim(JwtService.CLAIM_ROLE).asString()
                if (userId.isNullOrBlank() || role.isNullOrBlank()) {
                    null
                } else {
                    runCatching { UserPrincipal(userId, Role.valueOf(role)) }.getOrNull()
                }
            }

            // Bez ovoga Ktor vraca prazno telo, pa klijent ne zna sta se desilo.
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("UNAUTHORIZED", "Nedostaje ili je istekao token.")
                )
            }
        }
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
        authRoutes(jwtService)
        apiRoutes()
    }
}
