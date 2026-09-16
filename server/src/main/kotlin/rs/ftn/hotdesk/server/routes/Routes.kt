package rs.ftn.hotdesk.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import rs.ftn.hotdesk.server.auth.JwtService
import rs.ftn.hotdesk.server.auth.requireAdmin
import rs.ftn.hotdesk.server.auth.user
import rs.ftn.hotdesk.server.service.BookingResult
import rs.ftn.hotdesk.server.service.BookingService
import rs.ftn.hotdesk.server.service.ResourceService
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.ErrorResponse
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.UpsertResourceRequest

/**
 * AUTENTIKACIJA (faza 2).
 *
 * Identitet i uloga dolaze iz potpisanog JWT tokena, koji se dobija na
 * POST /api/auth/login. Zaglavlje X-User-Id iz faze 1 vise ne postoji - bilo je
 * privremeno resenje kojim je svako mogao da se predstavi kao bilo ko.
 *
 * Rute su podeljene u tri grupe:
 *  - javne: citanje resursa i dnevne mreze slotova (nikakav token);
 *  - korisnicke: rad sa sopstvenim rezervacijama (bilo koji ispravan token);
 *  - administratorske: izmena resursa i globalni pregled (uloga ADMIN).
 *
 * Zahtev bez tokena Ktor odbija sa 401 pre tela rute. Zahtev sa ispravnim tokenom
 * ali nedovoljnom ulogom dobija 403 iz requireAdmin().
 */

fun Route.apiRoutes() {

    route("/api/resources") {

        get {
            val type = call.request.queryParameters["type"]?.let { ResourceType.valueOf(it) }
            val location = call.request.queryParameters["location"]
            val activeOnly = call.request.queryParameters["activeOnly"]?.toBooleanStrictOrNull() ?: true
            call.respond(ResourceService.list(type, location, activeOnly))
        }

        get("/{id}") {
            val id = call.parameters["id"]!!
            val resource = ResourceService.byId(id)
            if (resource == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))
            } else {
                call.respond(resource)
            }
        }

        /** Dnevna mreza slotova. `at` je bilo koji trenutak u trazenom danu (epoch millis). */
        get("/{id}/availability") {
            val id = call.parameters["id"]!!
            val at = call.request.queryParameters["at"]?.toLongOrNull() ?: System.currentTimeMillis()
            call.respond(BookingService.availability(id, at))
        }

        // --- Administrator ---
        // Ispravan token je uslov da se udje u blok; uloga se proverava po ruti.

        authenticate(JwtService.CONFIG_NAME) {

            post {
                if (!call.requireAdmin()) return@post
                val req = call.receive<UpsertResourceRequest>()
                call.respond(HttpStatusCode.Created, ResourceService.create(req))
            }

            put("/{id}") {
                if (!call.requireAdmin()) return@put
                val id = call.parameters["id"]!!
                val updated = ResourceService.update(id, call.receive())
                if (updated == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))
                } else {
                    call.respond(updated)
                }
            }

            post("/{id}/deactivate") {
                if (!call.requireAdmin()) return@post
                val id = call.parameters["id"]!!
                if (ResourceService.setActive(id, false)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))
                }
            }

            post("/{id}/activate") {
                if (!call.requireAdmin()) return@post
                val id = call.parameters["id"]!!
                if (ResourceService.setActive(id, true)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))
                }
            }
        }
    }

    // Nijedna ruta nad rezervacijama nije javna - sve traze ispravan token.
    authenticate(JwtService.CONFIG_NAME) {
        route("/api/bookings") {

            post {
                val userId = call.user().userId

                val req = call.receive<CreateBookingRequest>()
                when (val result = BookingService.create(userId, req)) {
                    is BookingResult.Created ->
                        call.respond(HttpStatusCode.Created, result.booking)

                    is BookingResult.Invalid ->
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(result.code, result.message))

                    BookingResult.ResourceNotFound ->
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))

                    BookingResult.ResourceInactive ->
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("INACTIVE", "Resurs trenutno nije u upotrebi."))

                    // Ovde se materijalizuje resenje konkurentnosti:
                    // jedinstveni indeks je odbio upis, jer je neko drugi bio brzi.
                    BookingResult.SlotTaken ->
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("SLOT_TAKEN", "Termin je upravo zauzet."))
                }
            }

            get("/mine") {
                call.respond(BookingService.forUser(call.user().userId))
            }

            /** Globalni pregled sa filterima. Samo ADMIN. */
            get {
                if (!call.requireAdmin()) return@get
                call.respond(
                    BookingService.all(
                        userId = call.request.queryParameters["userId"],
                        resourceId = call.request.queryParameters["resourceId"]
                    )
                )
            }

            delete("/{id}") {
                val principal = call.user()
                val id = call.parameters["id"]!!
                // Administrator sme da otkaze tudju rezervaciju; korisnik samo svoju.
                // Samu proveru vlasnistva radi BookingService.cancel.
                if (BookingService.cancel(id, principal.userId, isAdmin = principal.isAdmin)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Rezervacija ne postoji."))
                }
            }
        }
    }
}
