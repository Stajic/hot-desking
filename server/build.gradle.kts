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