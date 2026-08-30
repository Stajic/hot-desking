package rs.ftn.hotdesk.server.db

import io.ktor.server.application.Application
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Podrazumevano se koristi H2 u fajlu: nula instalacije, radi na svakoj masini,
 * pa i na skolskoj bez administratorskih prava i bez Docker-a.
 *
 * PostgreSQL se ukljucuje iskljucivo izmenom application.conf - nijedna linija
 * aplikativnog koda ne zna koja baza je ispod. Resenje konkurentnosti
 * (jedinstveni indeks) radi na obe.
 */
object DatabaseFactory {

    fun init(app: Application) {
        val cfg = app.environment.config
        val driver = cfg.property("storage.driver").getString()
        val url = cfg.property("storage.jdbcUrl").getString()
        val user = cfg.property("storage.user").getString()
        val password = cfg.property("storage.password").getString()

        Database.connect(url = url, driver = driver, user = user, password = password)

        transaction {
            SchemaUtils.create(Users, Resources, ResourceAmenities, Bookings, BookingSlots)
            Seed.runIfEmpty()
        }
    }
}
