package rs.ftn.hotdesk.server.db

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import rs.ftn.hotdesk.server.auth.Passwords
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.Role
import java.util.UUID

/**
 * Pocetni podaci. Bez ovoga prvi ekran aplikacije je prazan, sto je losa demonstracija.
 *
 * Od faze 2 lozinke prolaze kroz BCrypt (cena 12) i u bazi stoji samo hash.
 * Vrednosti ispod su demonstracione i sluze iskljucivo za lokalno pokretanje.
 */
object Seed {

    const val ADMIN_EMAIL = "admin@firma.rs"
    const val USER_EMAIL = "pera@firma.rs"

    fun runIfEmpty() {
        if (Resources.selectAll().limit(1).any()) return

        val adminId = UUID.randomUUID().toString()
        val userId = UUID.randomUUID().toString()

        Users.insert {
            it[id] = adminId
            it[name] = "Administrator"
            it[email] = ADMIN_EMAIL
            it[passwordHash] = Passwords.hash("admin123")
            it[role] = Role.ADMIN.name
        }
        Users.insert {
            it[id] = userId
            it[name] = "Pera Peric"
            it[email] = USER_EMAIL
            it[passwordHash] = Passwords.hash("pera123")
            it[role] = Role.USER.name
        }

        data class SeedResource(
            val name: String,
            val type: ResourceType,
            val location: String,
            val capacity: Int,
            val amenities: List<String>,
            val active: Boolean = true
        )

        val seed = listOf(
            SeedResource("Sto A-01", ResourceType.DESK, "Sprat 1 - Open space", 1, listOf("monitor 27\"", "dok stanica")),
            SeedResource("Sto A-02", ResourceType.DESK, "Sprat 1 - Open space", 1, listOf("monitor 27\"")),
            SeedResource("Sto A-03", ResourceType.DESK, "Sprat 1 - Open space", 1, emptyList()),
            SeedResource("Sto B-01", ResourceType.DESK, "Sprat 2 - Tihi deo", 1, listOf("dva monitora", "podesiva visina")),
            SeedResource("Sto B-02", ResourceType.DESK, "Sprat 2 - Tihi deo", 1, listOf("podesiva visina"), active = false),
            SeedResource("Sala Dunav", ResourceType.MEETING_ROOM, "Sprat 1", 8, listOf("projektor", "tabla", "video konferencija")),
            SeedResource("Sala Sava", ResourceType.MEETING_ROOM, "Sprat 2", 4, listOf("tabla")),
            SeedResource("Sala Tisa", ResourceType.MEETING_ROOM, "Sprat 3", 14, listOf("projektor", "razglas", "video konferencija"))
        )

        seed.forEach { r ->
            val rid = UUID.randomUUID().toString()
            Resources.insert {
                it[id] = rid
                it[name] = r.name
                it[type] = r.type.name
                it[location] = r.location
                it[capacity] = r.capacity
                it[isActive] = r.active
            }
            r.amenities.forEach { a ->
                ResourceAmenities.insert {
                    it[resourceId] = rid
                    it[amenity] = a
                }
            }
        }
    }
}
