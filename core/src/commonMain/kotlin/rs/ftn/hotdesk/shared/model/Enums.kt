package rs.ftn.hotdesk.shared.model

import kotlinx.serialization.Serializable

/**
 * Enumeracije zive u :shared modulu namerno.
 *
 * Da su ovo String-ovi (kako je bilo u prvoj verziji specifikacije), server bi mogao
 * da posalje "meeting_room", a klijent da ocekuje "MEETING_ROOM", i to bi se videlo
 * tek u runtime-u. Ovako kompajler odbija da izgradi projekat ako se dve strane raziđu.
 *
 * Ovo je najkonkretniji argument za postojanje deljenog modula.
 */

@Serializable
enum class Role { ADMIN, USER }

@Serializable
enum class ResourceType { DESK, MEETING_ROOM }

@Serializable
enum class BookingStatus { CONFIRMED, CANCELLED }
