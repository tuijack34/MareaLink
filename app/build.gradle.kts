import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Configuración local (no se sube al repo): ver local.properties.example
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun local(key: String): String? = localProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

// Opcional: si no hay Client ID, la app lo pide la primera vez que se abre.
// Cambiar versionName publica una Release nueva en GitHub (ver .github/workflows/release.yml).
// MAREA_PUBLIC=1 compila un APK sin Client ID aunque tengas uno en local.properties (para publicar).
val tidalClientId = if (System.getenv("MAREA_PUBLIC") == "1") "" else
    (local("tidal.clientId") ?: System.getenv("TIDAL_CLIENT_ID") ?: "")

android {
    namespace = "uy.gero.marea"
    compileSdk = 36

    defaultConfig {
        applicationId = "uy.gero.marealink"
        minSdk = 29
        targetSdk = 36
        versionCode = 25
        versionName = "link-0.9.1"
        buildConfigField("String", "TIDAL_CLIENT_ID", "\"$tidalClientId\"")
        buildConfigField("String", "TIDAL_REDIRECT_URI", "\"tidalfold://link\"")
        buildConfigField(
            "String",
            "TIDAL_SCOPES",
            "\"user.read collection.read collection.write playlists.read playlists.write playback search.read recommendations.read entitlements.read\"",
        )
    }

    // Firma propia opcional. Sin esto, el release se firma con la clave de debug de tu máquina.
    val storePath = local("signing.storeFile")
    if (storePath != null) {
        signingConfigs {
            create("propia") {
                storeFile = rootProject.file(storePath)
                storePassword = local("signing.storePassword")
                keyAlias = local("signing.keyAlias")
                keyPassword = local("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (storePath != null) signingConfigs.getByName("propia") else signingConfigs.getByName("debug")
        }
        debug {
            if (storePath != null) signingConfig = signingConfigs.getByName("propia")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/*.kotlin_module", "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation("com.tidal.sdk:auth:0.11.3")

    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
