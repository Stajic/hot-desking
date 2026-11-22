import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

/*
 * Deljeni klijentski kod (faza 5): ono sto je isto za aplikaciju na telefonu i za
 * ekran na STB uredjaju, a ne pripada serveru. Model i pravila su u :core.
 *
 * Do faze 5 ovde je stajao samo primer koji generise Kotlin Multiplatform
 * carobnjak; uklonjen je, kao i Compose, jer deljeni sloj nema korisnicki interfejs.
 */
kotlin {

    android {
        namespace = "rs.ftn.hotdesk.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        // commonTest se izvrsava kao test na JVM-u, bez uredjaja.
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            // HttpClient je deo javnog API-ja modula (hotdeskHttpClient, PublicApi).
            api(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.serializationKotlinxJsonMp)
        }
        // Jedini platformski deo: HTTP engine.
        androidMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
        }
    }
}
