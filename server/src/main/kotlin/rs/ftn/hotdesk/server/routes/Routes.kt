package rs.ftn.hotdesk.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import rs.ftn.hotdesk.server.service.BookingResult
import rs.ftn.hotdesk.server.service.BookingService
import rs.ftn.hotdesk.server.service.ResourceService
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.ErrorResponse
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.UpsertResourceRequest

/**
 * PRIVREMENA AUTENTIKACIJA (faza 1).
 *
 * Identitet se cita iz zaglavlja X-User-Id. To NIJE autentikacija - svako moze da
 * posalje tudji ID. Postoji samo da bi PoC bio pokretljiv bez JWT infrastrukture.
 *
 * FAZA 2: Ktor Authentication plugin sa JWT-om, BCrypt za lozinke, i uloga iz
 * tokena umesto iz zaglavlja. Sve tacke koje ovo koriste su obelezene sa TODO(auth).
 */
private const val USER_HEADER = "X-User-Id"

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
        // TODO(auth): zastititi ulogom ADMIN kad se uvede JWT.

        post {
            val req = call.receive<UpsertResourceRequest>()
            call.respond(HttpStatusCode.Created, ResourceService.create(req))
        }

        put("/{id}") {
            val id = call.parameters["id"]!!
            val updated = ResourceService.update(id, call.receive())
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))
            } else {
                call.respond(updated)
            }
        }

        post("/{id}/deactivate") {
            val id = call.parameters["id"]!!
            if (ResourceService.setActive(id, false)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))
            }
        }

        post("/{id}/activate") {
            val id = call.parameters["id"]!!
            if (ResourceService.setActive(id, true)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Resurs ne postoji."))
            }
        }
    }

    route("/api/bookings") {

        post {
            val userId = call.request.headers[USER_HEADER] // TODO(auth): iz JWT tokena
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("NO_USER", "Nedostaje $USER_HEADER."))
                return@post
            }

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
            val userId = call.request.headers[USER_HEADER]
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("NO_USER", "Nedostaje $USER_HEADER."))
                return@get
            }
            call.respond(BookingService.forUser(userId))
        }

        /** Globalni pregled sa filterima. TODO(auth): samo ADMIN. */
        get {
            call.respond(
                BookingService.all(
                    userId = call.request.queryParameters["userId"],
                    resourceId = call.request.queryParameters["resourceId"]
                )
            )
        }

        delete("/{id}") {
            val userId = call.request.headers[USER_HEADER]
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("NO_USER", "Nedostaje $USER_HEADER."))
                return@delete
            }
            val id = call.parameters["id"]!!
            // TODO(auth): isAdmin iz tokena umesto false
            if (BookingService.cancel(id, userId, isAdmin = false)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Rezervacija ne postoji."))
            }
        }
    }
}
