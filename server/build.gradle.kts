plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
}

group = "rs.ftn.hotdesk"
version = "1.0.0"
application {
    mainClass = "rs.ftn.hotdesk.server.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)

    // --- dodato za hot-desking ---
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    // PostgreSQL drajver (faza 4). Kod ga ne poziva direktno - JDBC ga ucitava po
    // imenu klase iz konfiguracije - pa je potreban samo u vreme izvrsavanja.
    runtimeOnly(libs.postgresql)

    // --- autentikacija, faza 2 ---
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.bcrypt)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)

    // Pravi PostgreSQL za testove (faza 4): pokrece se iz binarnih fajlova preuzetih
    // kao zavisnost, bez instalacije i bez Docker-a. BOM bira verziju PostgreSQL-a.
    testImplementation(libs.zonky.embeddedPostgres)
    testImplementation(platform(libs.zonky.postgresBinariesBom))
}

// Isti testovi nad pravim PostgreSQL-om (faza 4); `test` ostaje nad H2.
// Bazu bira TestDatabase prema sistemskom svojstvu hotdesk.test.db.
val testPostgres by tasks.registering(Test::class) {
    description = "Pokrece testove servera nad pravim PostgreSQL-om (embedded-postgres)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("hotdesk.test.db", "postgres")
}

// Negativni prolaz (faza 4): koliko rezervacija istog termina prodje kroz naivnu
// proveru-pa-upis bez jedinstvenog indeksa. Merenje, ne test - trka je nepredvidiva.
// Pokretanje: .\gradlew.bat :server:negativniProlaz [-Pbaza=postgres]
val negativniProlaz by tasks.registering(JavaExec::class) {
    description = "Meri TOCTOU: koliko rezervacija istog termina prodje bez jedinstvenog indeksa."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rs.ftn.hotdesk.server.NegativniProlazKt"
    systemProperty("hotdesk.test.db", providers.gradleProperty("baza").getOrElse("h2"))
    args(layout.buildDirectory.dir("negativni-prolaz").get().asFile.absolutePath)
}

// PostgreSQL za merenje servera (faza 4): isti embedded PostgreSQL kao u testovima,
// ali kao zaseban proces na portu 54320, dok se zadatak ne zaustavi.
val postgresZaMerenje by tasks.registering(JavaExec::class) {
    description = "Pokrece PostgreSQL 18.6 na portu 54320 sa praznim bazama za merenje."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rs.ftn.hotdesk.server.PostgresZaMerenjeKt"
}