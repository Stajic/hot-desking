package rs.ftn.hotdesk.shared.model

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Entiteti
// ---------------------------------------------------------------------------

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val role: Role
)

@Serializable
data class ResourceDto(
    val id: String,
    val name: String,
    val type: ResourceType,
    val location: String,
    val capacity: Int,
    val amenities: List<String> = emptyList(),
    val isActive: Boolean
)

/**
 * [resourceName] i [userName] su namerno denormalizovani.
 *
 * Bez njih bi ekran "Moje rezervacije" morao da povuce listu rezervacija, pa zatim
 * poseban zahtev po svakom resursu da bi prikazao ime - klasican N+1 problem nad mrezom,
 * gde je svaki round-trip skup. Cena je da su imena kopija stanja u trenutku citanja.
 */
@Serializable
data class BookingDto(
    val id: String,
    val resourceId: String,
    val resourceName: String,
    val userId: String,
    val userName: String,
    val startTime: Long,   // epoch millis, poravnato na granicu slota
    val endTime: Long,     // epoch millis, ekskluzivno
    val status: BookingStatus
)

// ---------------------------------------------------------------------------
// Zahtevi i odgovori
// ---------------------------------------------------------------------------

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserDto
)

@Serializable
data class CreateBookingRequest(
    val resourceId: String,
    val startTime: Long,
    val endTime: Long
)

@Serializable
data class UpsertResourceRequest(
    val name: String,
    val type: ResourceType,
    val location: String,
    val capacity: Int,
    val amenities: List<String> = emptyList(),
    val isActive: Boolean = true
)

/** Jedan slot u dnevnoj mrezi dostupnosti. */
@Serializable
data class SlotDto(
    val startTime: Long,
    val endTime: Long,
    val isAvailable: Boolean
)

/** Cela dnevna mreza za jedan resurs - ono sto klijent crta kao grid. */
@Serializable
data class AvailabilityDto(
    val resourceId: String,
    val dayStart: Long,
    val slots: List<SlotDto>
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)
