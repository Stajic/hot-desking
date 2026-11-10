package rs.ftn.hotdesk.shared.rules

import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.UpsertResourceRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Kao i [BookingRulesTest], izvrsava se na svakom targetu :core modula: ista provera
 * koju radi forma na telefonu i server pre upisa.
 */
class ResourceRulesTest {

    private val sto = UpsertResourceRequest(
        name = "Sto C-01",
        type = ResourceType.DESK,
        location = "Sprat 3 - Open space",
        capacity = 1,
        amenities = listOf("monitor 27\"")
    )

    private val sala = UpsertResourceRequest(
        name = "Sala Morava",
        type = ResourceType.MEETING_ROOM,
        location = "Sprat 4",
        capacity = 6,
        amenities = listOf("projektor", "tabla")
    )

    /** Pomocna: ocekuje odbijanje sa datim kodom polja. */
    private fun assertInvalid(code: String, req: UpsertResourceRequest) {
        val r = ResourceRules.validate(req)
        assertIs<ResourceRules.Result.Invalid>(r)
        assertEquals(code, r.code)
    }

    @Test
    fun prihvata_ispravan_sto_i_salu() {
        assertEquals(ResourceRules.Result.Valid, ResourceRules.validate(sto))
        assertEquals(ResourceRules.Result.Valid, ResourceRules.validate(sala))
    }

    @Test
    fun odbija_prazan_naziv() {
        assertInvalid("NAME", sala.copy(name = ""))
        assertInvalid("NAME", sala.copy(name = "   "))
    }

    @Test
    fun naziv_na_granici_prolazi_a_preko_nje_ne() {
        assertEquals(ResourceRules.Result.Valid, ResourceRules.validate(sala.copy(name = "S".repeat(120))))
        assertInvalid("NAME", sala.copy(name = "S".repeat(121)))
    }

    @Test
    fun odbija_praznu_i_predugacku_lokaciju() {
        assertInvalid("LOCATION", sala.copy(location = " "))
        assertInvalid("LOCATION", sala.copy(location = "L".repeat(121)))
    }

    @Test
    fun sto_mora_imati_tacno_jedno_mesto() {
        assertInvalid("CAPACITY", sto.copy(capacity = 0))
        assertInvalid("CAPACITY", sto.copy(capacity = 2))
    }

    @Test
    fun sala_ima_od_dva_do_sto_mesta() {
        assertInvalid("CAPACITY", sala.copy(capacity = 1))
        assertEquals(ResourceRules.Result.Valid, ResourceRules.validate(sala.copy(capacity = 2)))
        assertEquals(ResourceRules.Result.Valid, ResourceRules.validate(sala.copy(capacity = 100)))
        assertInvalid("CAPACITY", sala.copy(capacity = 101))
    }

    @Test
    fun odbija_previse_opreme() {
        assertEquals(
            ResourceRules.Result.Valid,
            ResourceRules.validate(sala.copy(amenities = (1..10).map { "stavka $it" }))
        )
        assertInvalid("AMENITIES", sala.copy(amenities = (1..11).map { "stavka $it" }))
    }

    @Test
    fun odbija_praznu_i_predugacku_stavku_opreme() {
        assertInvalid("AMENITIES", sala.copy(amenities = listOf("projektor", " ")))
        assertInvalid("AMENITIES", sala.copy(amenities = listOf("O".repeat(61))))
    }
}
