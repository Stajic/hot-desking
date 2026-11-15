package rs.ftn.hotdesk.server

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.UUID

/**
 * Baza za testove: H2 u memoriji (podrazumevano) ili pravi PostgreSQL.
 *
 * Bira se sistemskim svojstvom `hotdesk.test.db`, koje postavlja Gradle:
 * `:server:test` radi nad H2, `:server:testPostgres` nad PostgreSQL-om. Testovi su
 * isti, razlikuje se samo baza ispod - tako se proverava da jedinstveni indeks i
 * ostatak servera rade isto na obe baze.
 *
 * PostgreSQL je embedded-postgres (io.zonky.test): pravi PostgreSQL server koji se
 * pokrece iz binarnih fajlova preuzetih kao Gradle zavisnost, bez instalacije i bez
 * Docker-a. Jedan server po JVM-u; svaki test dobija svoju, novu bazu na njemu.
 */
object TestDatabase {

    data class Connection(val driver: String, val url: String, val user: String, val password: String)

    val isPostgres: Boolean = System.getProperty("hotdesk.test.db") == "postgres"

    /** Pokrece se pri prvom koriscenju i gasi na kraju JVM-a. */
    private val postgres: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().start().also { pg ->
            Runtime.getRuntime().addShutdownHook(Thread { pg.close() })
        }
    }

    /** Nova, prazna baza - za svaki test posebno, kao H2 u memoriji. */
    fun fresh(): Connection {
        val name = "test_" + UUID.randomUUID().toString().replace("-", "")
        if (!isPostgres) {
            return Connection("org.h2.Driver", "jdbc:h2:mem:$name;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
        }
        postgres.postgresDatabase.connection.use { c ->
            c.createStatement().use { it.execute("CREATE DATABASE $name") }
        }
        return Connection("org.postgresql.Driver", postgres.getJdbcUrl("postgres", name), "postgres", "")
    }

    /** Nova baza i veza sa njom; Exposed je koristi za transakcije koje slede. */
    fun connect(): Database {
        val c = fresh()
        return Database.connect(url = c.url, driver = c.driver, user = c.user, password = c.password)
    }
}
