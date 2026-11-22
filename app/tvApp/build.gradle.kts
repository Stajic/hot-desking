import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Ekran sa informacijama o zauzetosti sale, za STB uredjaj sa Android TV-om (faza 5).
 *
 * Samo prikaz, bez prijave i bez rezervisanja. Mrezni sloj (HTTP klijent, javne rute,
 * format vremena) dolazi iz :app:shared, a model i racun statusa sale iz :core -
 * isti kod koji koriste server i aplikacija na telefonu.
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":app:shared"))

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.activity.compose)
}

android {
    namespace = "rs.ftn.hotdesk.tv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Zasebna aplikacija, pa telefon i TV mogu biti instalirani na istom uredjaju.
        applicationId = "rs.ftn.hotdesk.tv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
