package rs.ftn.hotdesk.server

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

/*
 * PostgreSQL za merenje servera (faza 4).
 *
 * Isti PostgreSQL 18.6 koji koriste testovi (embedded-postgres), ali kao zaseban
 * proces na fiksnom portu, da bi se server merio nad njim kao nad pravom bazom i da
 * se memorija baze merila odvojeno od memorije servera.
 *
 * Pravi prazne baze hotdesk_1 ... hotdesk_N, po jednu za svako merenje: hladan start
 * servera trazi praznu bazu (sema i pocetni podaci), kao novi folder kod H2.
 * Ispisuje i stvarna podesavanja baze, jer embedded-postgres neka od njih postavlja
 * drugacije od podrazumevanog PostgreSQL-a. Radi dok se proces ne zaustavi.
 *
 * Pokretanje: .\gradlew.bat :server:postgresZaMerenje
 * Server se zatim usmerava na bazu promenljivama DB_DRIVER, DB_URL, DB_USER.
 *
 * Zaustavljanje: Ctrl+C. Ako se proces ubije naglo, PostgreSQL ostaje da radi bez
 * nadzora; tada se gasi sa `pg_ctl stop -D <direktorijum podataka> -m fast`, gde se
 * direktorijum cita iz komandne linije procesa postgres.exe.
 */

private const val PORT = 54320
private const val BAZA = 10

fun main() {
    // embedded-postgres za brzinu testova iskljucuje trajnost upisa (fsync=off,
    // synchronous_commit=off). Za merenje se vracaju podrazumevana podesavanja
    // PostgreSQL-a, sa kojima bi radio i u stvarnoj upotrebi.
    val pg = EmbeddedPostgres.builder()
        .setPort(PORT)
        .setServerConfig("fsync", "on")
        .setServerConfig("synchronous_commit", "on")
        .start()
    Runtime.getRuntime().addShutdownHook(Thread { pg.close() })

    pg.postgresDatabase.connection.use { c ->
        c.createStatement().use { st ->
            (1..BAZA).forEach { st.execute("CREATE DATABASE hotdesk_$it") }
            listOf("server_version", "synchronous_commit", "fsync", "shared_buffers", "max_connections").forEach { p ->
                st.executeQuery("SHOW $p").use { rs -> rs.next(); println("$p = ${rs.getString(1)}") }
            }
        }
    }
    println("baze: hotdesk_1 ... hotdesk_$BAZA, korisnik postgres, bez lozinke")
    println("SPREMAN jdbc:postgresql://localhost:$PORT/hotdesk_1")

    Thread.currentThread().join()
}
