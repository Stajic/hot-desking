package rs.ftn.hotdesk.server

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import rs.ftn.hotdesk.server.db.Seed
import rs.ftn.hotdesk.shared.model.BookingDto
import rs.ftn.hotdesk.shared.model.BookingStatus
import rs.ftn.hotdesk.shared.model.CreateBookingRequest
import rs.ftn.hotdesk.shared.model.ErrorResponse
import rs.ftn.hotdesk.shared.model.LoginRequest
import rs.ftn.hotdesk.shared.model.LoginResponse
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.UpsertResourceRequest
import rs.ftn.hotdesk.shared.rules.BookingRules
import rs.ftn.hotdesk.shared.rules.ResourceRules
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Administratorske rute, kroz HTTP.
 *
 * Za razliku od [ConcurrencyTest], koji poziva servis direktno, ovi testovi prolaze
 * kroz ceo server: rutiranje, JWT, proveru uloge, serijalizaciju i StatusPages.
 * Svaki test dobija svezu H2 bazu u memoriji, sa pocetnim podacima iz Seed-a.
 *
 * Tela zahteva i odgovora se serijalizuju rucno, preko kotlinx.serialization, da
 * test ne bi trazio novu zavisnost (ContentNegotiation za klijent).
 */
class AdminRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val resourceList = ListSerializer(ResourceDto.serializer())
    private val bookingList = ListSerializer(BookingDto.serializer())

    private fun config() = MapApplicationConfig(
        "storage.driver" to "org.h2.Driver",
        "storage.jdbcUrl" to "jdbc:h2:mem:admin_${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "storage.user" to "sa",
        "storage.password" to "",
        "jwt.secret" to "testna-tajna",
        "jwt.issuer" to "hot-desking",
        "jwt.audience" to "hot-desking-klijenti",
        "jwt.realm" to "hot-desking",
        "jwt.validityHours" to "1"
    )

    private fun serverTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = config() }
        application { module() }
        block()
    }

    // --- pomocne ---

    private suspend fun ApplicationTestBuilder.login(email: String, password: String): String {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password)))
        }
        assertEquals(HttpStatusCode.OK, response.status, "Prijava demo naloga mora da uspe.")
        return json.decodeFromString(LoginResponse.serializer(), response.bodyAsText()).token
    }

    private suspend fun ApplicationTestBuilder.admin() = login(Seed.ADMIN_EMAIL, "admin123")
    private suspend fun ApplicationTestBuilder.pera() = login(Seed.USER_EMAIL, "pera123")

    private suspend fun ApplicationTestBuilder.createResource(token: String, req: UpsertResourceRequest): HttpResponse =
        client.post("/api/resources") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(UpsertResourceRequest.serializer(), req))
        }

    private suspend fun ApplicationTestBuilder.resources(query: String = ""): List<ResourceDto> =
        json.decodeFromString(resourceList, client.get("/api/resources$query").bodyAsText())

    private suspend fun ApplicationTestBuilder.book(token: String, req: CreateBookingRequest): HttpResponse =
        client.post("/api/bookings") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateBookingRequest.serializer(), req))
        }

    private suspend fun HttpResponse.error(): ErrorResponse =
        json.decodeFromString(ErrorResponse.serializer(), bodyAsText())

    private val novaSala = UpsertResourceRequest(
        name = "Sala Morava",
        type = ResourceType.MEETING_ROOM,
        location = "Sprat 4",
        capacity = 6,
        amenities = listOf("tabla")
    )

    // --- uloge ---

    @Test
    fun korisnik_bez_administratorske_uloge_dobija_403() = serverTest {
        val pre = resources("?activeOnly=false").size

        val response = createResource(pera(), novaSala)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("FORBIDDEN", response.error().code)
        assertEquals(pre, resources("?activeOnly=false").size, "Resurs ne sme biti upisan.")
    }

    @Test
    fun administrator_dodaje_ispravan_resurs() = serverTest {
        val response = createResource(admin(), novaSala)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(true, resources().any { it.name == "Sala Morava" })
    }

    // --- pravila za resurs ---

    /**
     * Naziv duzi od kolone u bazi (120 znakova). Bez pravila ga odbija tek Exposed,
     * kodom BAD_REQUEST i porukom na engleskom ("Value can't be stored to database
     * column because exceeds length"), iz koje klijent ne zna koje polje da ispravi.
     */
    @Test
    fun predugacak_naziv_odbija_pravilo_a_ne_baza() = serverTest {
        val response = createResource(admin(), novaSala.copy(name = "S".repeat(121)))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("NAME", response.error().code)
    }

    @Test
    fun sto_sa_vise_od_jednog_mesta_se_odbija() = serverTest {
        val sto = novaSala.copy(name = "Sto C-01", type = ResourceType.DESK, capacity = 8)

        val response = createResource(admin(), sto)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("CAPACITY", response.error().code)
    }

    @Test
    fun izmena_prolazi_kroz_ista_pravila() = serverTest {
        val sava = resources().first { it.name == "Sala Sava" }

        val response = client.put("/api/resources/${sava.id}") {
            bearerAuth(admin())
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    UpsertResourceRequest.serializer(),
                    UpsertResourceRequest(
                        name = "  ",
                        type = sava.type,
                        location = sava.location,
                        capacity = sava.capacity,
                        amenities = sava.amenities
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("NAME", response.error().code)
        assertEquals("Sala Sava", resources().first { it.id == sava.id }.name, "Naziv ne sme biti izmenjen.")
    }

    /** Pravila ne smeju da odbace podatke koji vec postoje - ni neaktivan resurs. */
    @Test
    fun pocetni_podaci_ispunjavaju_pravila() = serverTest {
        val svi = resources("?activeOnly=false")
        assertEquals(8, svi.size)

        svi.forEach { r ->
            val req = UpsertResourceRequest(r.name, r.type, r.location, r.capacity, r.amenities, r.isActive)
            assertEquals(ResourceRules.Result.Valid, ResourceRules.validate(req), r.name)
        }
    }

    // --- deaktivacija ---

    /**
     * Odluka iz ResourceService.setActive, dokazana: deaktivacija sklanja resurs iz
     * ponude za nove rezervacije, a postojece ne dira.
     */
    @Test
    fun deaktivacija_odbija_nove_rezervacije_a_postojece_ostavlja() = serverTest {
        val sava = resources().first { it.name == "Sala Sava" }
        val korisnik = pera()

        val sutra = BookingRules.dayStart(System.currentTimeMillis() + 24 * 3_600_000L)
        val prvi = sutra + 2 * BookingRules.SLOT_MILLIS      // 09:00
        val drugi = sutra + 6 * BookingRules.SLOT_MILLIS     // 11:00
        val sat = 2 * BookingRules.SLOT_MILLIS

        val postojeca = book(korisnik, CreateBookingRequest(sava.id, prvi, prvi + sat))
        assertEquals(HttpStatusCode.Created, postojeca.status)

        val deaktivacija = client.post("/api/resources/${sava.id}/deactivate") { bearerAuth(admin()) }
        assertEquals(HttpStatusCode.NoContent, deaktivacija.status)

        val nova = book(korisnik, CreateBookingRequest(sava.id, drugi, drugi + sat))
        assertEquals(HttpStatusCode.Conflict, nova.status)
        assertEquals("INACTIVE", nova.error().code)

        val moje = json.decodeFromString(
            bookingList,
            client.get("/api/bookings/mine") { bearerAuth(korisnik) }.bodyAsText()
        )
        assertEquals(1, moje.size)
        assertEquals(BookingStatus.CONFIRMED, moje.single().status, "Postojeca rezervacija ostaje potvrdjena.")
    }

    // --- filteri ---

    @Test
    fun filter_po_opremi() = serverTest {
        assertEquals(
            setOf("Sala Dunav", "Sala Tisa"),
            resources("?amenity=projektor").map { it.name }.toSet()
        )
        // Vise stavki opreme: resurs mora da ima svaku od njih.
        assertEquals(
            setOf("Sala Tisa"),
            resources("?amenity=projektor&amenity=razglas").map { it.name }.toSet()
        )
    }
}
