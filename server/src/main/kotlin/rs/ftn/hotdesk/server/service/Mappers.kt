package rs.ftn.hotdesk.server.service

import org.jetbrains.exposed.v1.core.ResultRow
import rs.ftn.hotdesk.server.db.Bookings
import rs.ftn.hotdesk.server.db.Resources
import rs.ftn.hotdesk.server.db.Users
import rs.ftn.hotdesk.shared.model.BookingDto
import rs.ftn.hotdesk.shared.model.BookingStatus
import rs.ftn.hotdesk.shared.model.ResourceDto
import rs.ftn.hotdesk.shared.model.ResourceType
import rs.ftn.hotdesk.shared.model.Role
import rs.ftn.hotdesk.shared.model.UserDto

/**
 * Granica izmedju sloja baze i sloja API-ja.
 *
 * Baza cuva enume kao String (prenosivo izmedju H2 i PostgreSQL-a bez native enum tipova),
 * a DTO ih izlaze kao prave enume. Konverzija se radi na tacno jednom mestu - ovde.
 */

fun ResultRow.toUserDto() = UserDto(
    id = this[Users.id],
    name = this[Users.name],
    email = this[Users.email],
    role = Role.valueOf(this[Users.role])
)

fun ResultRow.toResourceDto(amenities: List<String> = emptyList()) = ResourceDto(
    id = this[Resources.id],
    name = this[Resources.name],
    type = ResourceType.valueOf(this[Resources.type]),
    location = this[Resources.location],
    capacity = this[Resources.capacity],
    amenities = amenities,
    isActive = this[Resources.isActive]
)

fun ResultRow.toBookingDto() = BookingDto(
    id = this[Bookings.id],
    resourceId = this[Bookings.resourceId],
    resourceName = this[Resources.name],
    userId = this[Bookings.userId],
    userName = this[Users.name],
    startTime = this[Bookings.startTime],
    endTime = this[Bookings.endTime],
    status = BookingStatus.valueOf(this[Bookings.status])
)
