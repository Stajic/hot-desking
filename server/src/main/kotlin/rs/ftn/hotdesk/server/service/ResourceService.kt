package rs.ftn.hotdesk.server.service

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import rs.ftn.hotdesk.server.db.ResourceAmenities
import rs.ftn.hotdesk.server.db.Resources
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.UpsertResourceRequest
import java.util.UUID

object ResourceService {

    fun list(
        type: ResourceType? = null,
        location: String? = null,
        activeOnly: Boolean = true
    ): List<ResourceDto> = transaction {
        var q = Resources.selectAll()
        if (type != null) q = q.andWhere { Resources.type eq type.name }
        if (location != null) q = q.andWhere { Resources.location eq location }
        if (activeOnly) q = q.andWhere { Resources.isActive eq true }

        val rows = q.orderBy(Resources.name).toList()
        val amenities = amenitiesFor(rows.map { it[Resources.id] })
        rows.map { it.toResourceDto(amenities[it[Resources.id]].orEmpty()) }
    }

    fun byId(id: String): ResourceDto? = transaction {
        Resources.selectAll()
            .where { Resources.id eq id }
            .singleOrNull()
            ?.let { it.toResourceDto(amenitiesFor(listOf(id))[id].orEmpty()) }
    }

    fun create(req: UpsertResourceRequest): ResourceDto = transaction {
        val newId = UUID.randomUUID().toString()
        Resources.insert {
            it[id] = newId
            it[name] = req.name
            it[type] = req.type.name
            it[location] = req.location
            it[capacity] = req.capacity
            it[isActive] = req.isActive
        }
        replaceAmenities(newId, req.amenities)
        byIdInTx(newId)!!
    }

    fun update(id: String, req: UpsertResourceRequest): ResourceDto? = transaction {
        val changed = Resources.update({ Resources.id eq id }) {
            it[name] = req.name
            it[type] = req.type.name
            it[location] = req.location
            it[capacity] = req.capacity
            it[isActive] = req.isActive
        }
        if (changed == 0) return@transaction null
        replaceAmenities(id, req.amenities)
        byIdInTx(id)
    }

    /**
     * Deaktivacija resursa (renoviranje, kvar).
     *
     * ODLUKA: postojece rezervacije se NE diraju automatski.
     *
     * Alternativa je bila kaskadno otkazivanje, ali time bi korisnik izgubio termin
     * bez ikakvog obavestenja - a sistem za notifikacije ne postoji. Ovako se resurs
     * sklanja iz ponude za nove rezervacije, a administrator u globalnom pregledu vidi
     * koje rezervacije jos vise nad njim i moze da ih otkaze rucno.
     *
     * Ovo je svesna odluka, ne propust, i kao takva je dokumentovana.
     */
    fun setActive(id: String, active: Boolean): Boolean = transaction {
        Resources.update({ Resources.id eq id }) { it[isActive] = active } > 0
    }

    // --- interno ---

    private fun byIdInTx(id: String): ResourceDto? =
        Resources.selectAll()
            .where { Resources.id eq id }
            .singleOrNull()
            ?.let { it.toResourceDto(amenitiesFor(listOf(id))[id].orEmpty()) }

    private fun amenitiesFor(ids: List<String>): Map<String, List<String>> {
        if (ids.isEmpty()) return emptyMap()
        return ResourceAmenities.selectAll()
            .where { ResourceAmenities.resourceId inList ids }
            .groupBy({ it[ResourceAmenities.resourceId] }, { it[ResourceAmenities.amenity] })
    }

    private fun replaceAmenities(resourceId: String, amenities: List<String>) {
        ResourceAmenities.deleteWhere { ResourceAmenities.resourceId eq resourceId }
        amenities.distinct().forEach { a ->
            ResourceAmenities.insert {
                it[ResourceAmenities.resourceId] = resourceId
                it[amenity] = a
            }
        }
    }
}
