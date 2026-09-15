package rs.ftn.hotdesk.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.ftn.hotdesk.server.auth.JwtService
import rs.ftn.hotdesk.server.auth.Passwords
import rs.ftn.hotdesk.server.db.Users
import rs.ftn.hotdesk.server.service.toUserDto
import rs.ftn.hotdesk.shared.model.ErrorResponse
import rs.ftn.hotdesk.shared.model.LoginRequest
import rs.ftn.hotdesk.shared.model.LoginResponse

/**
 * Prijava. Zamenjuje zaglavlje X-User-Id iz faze 1 stvarnom autentikacijom.
 *
 * Dve odluke koje nisu ocigledne iz koda:
 *
 * 1. Nepostojeci email i pogresna lozinka vracaju ISTU poruku. Razlicite poruke
 *    bi napadacu rekle koji emailovi postoje u sistemu (user enumeration).
 *
 * 2. Kada korisnik ne postoji, BCrypt se svejedno izvrsava nad laznim hashom.
 *    Bez toga bi odgovor za nepostojeceg korisnika stizao znatno brze nego za
 *    postojeceg, pa bi se postojanje naloga otkrilo merenjem vremena odziva.
 */

/**
 * Hash nad kojim se racuna kada korisnik ne postoji.
 *
 * Namerno je upisana konstanta, a ne izracunata vrednost. Da se racuna lenjo, prvi
 * zahtev za nepostojecim nalogom posle svakog restarta trajao bi dvostruko duze
 * (izmereno: 513 ms naspram 256 ms), pa bi upravo taj zahtev odao da nalog ne postoji.
 * Da se racuna pri podizanju, svaki start bi bio sporiji za oko 250 ms.
 *
 * Ovo je BCrypt hash niske koja nije ničija lozinka; sluzi iskljucivo da provera
 * potrosi isto vreme kao i kod postojeceg naloga.
 */
private const val DUMMY_HASH = "\$2a\$12\$tYupS.Zom6xpGSveI1OGNuau7aWT5EV3Lrc3gwRsB6Q3/gxj67Uee"

fun Route.authRoutes(jwt: JwtService) {

    post("/api/auth/login") {
        val req = call.receive<LoginRequest>()
        val email = req.email.trim().lowercase()

        val row = transaction {
            Users.selectAll().where { Users.email eq email }.singleOrNull()
        }

        val ok = if (row == null) {
            Passwords.matches(req.password, DUMMY_HASH)  // namerno, radi konstantnog vremena
            false
        } else {
            Passwords.matches(req.password, row[Users.passwordHash])
        }

        if (!ok || row == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("BAD_CREDENTIALS", "Neispravan email ili lozinka.")
            )
            return@post
        }

        val user = row.toUserDto()
        call.respond(LoginResponse(token = jwt.issue(user.id, user.role), user = user))
    }
}
