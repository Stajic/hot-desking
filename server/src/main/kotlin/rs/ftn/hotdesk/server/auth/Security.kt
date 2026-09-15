package rs.ftn.hotdesk.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.ApplicationConfig
import rs.ftn.hotdesk.shared.model.Role
import java.util.Date

/**
 * Identitet izvucen iz tokena. Ktor 3 vise ne trazi marker interfejs za principal,
 * pa je ovo obicna data klasa koja se cita sa call.principal<UserPrincipal>().
 */
data class UserPrincipal(
    val userId: String,
    val role: Role
) {
    val isAdmin: Boolean get() = role == Role.ADMIN
}

/**
 * Lozinke.
 *
 * BCrypt je izabran jer je namerno spor i nosi so u samom zapisu hasha, pa ne
 * postoji zasebna kolona za so. Cena od 12 znaci 2^12 iteracija - oko 250 ms po
 * hashu na ovom hardveru. To je namera, ne propust: napadacu koji dodje do baze
 * pogadjanje postaje neprakticno.
 *
 * Posledica koju treba imati u vidu pri merenju: upis pocetnih podataka hashuje
 * dve lozinke, sto hladan start produzava za oko pola sekunde. Topao start,
 * gde baza vec postoji, time nije pogodjen.
 */
object Passwords {

    private const val COST = 12

    fun hash(plain: String): String =
        BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun matches(plain: String, hash: String): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hash).verified
}

/**
 * Izdavanje i provera JWT tokena.
 *
 * Tajna se cita iz konfiguracije, koja je preuzima iz promenljive okruzenja
 * JWT_SECRET ako je postavljena. Vrednost upisana u application.conf je
 * iskljucivo razvojna - repozitorijum je javan i tamo ne sme stajati prava tajna.
 */
class JwtService(config: ApplicationConfig) {

    private val secret = config.property("jwt.secret").getString()
    private val issuer = config.property("jwt.issuer").getString()
    private val audience = config.property("jwt.audience").getString()
    private val validityMillis =
        config.property("jwt.validityHours").getString().toLong() * 3_600_000L

    val realm: String = config.property("jwt.realm").getString()

    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun issue(userId: String, role: Role): String =
        JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId)
            .withClaim(CLAIM_ROLE, role.name)
            .withExpiresAt(Date(System.currentTimeMillis() + validityMillis))
            .sign(algorithm)

    companion object {
        const val CLAIM_ROLE = "role"
        const val CONFIG_NAME = "auth-jwt"
    }
}
