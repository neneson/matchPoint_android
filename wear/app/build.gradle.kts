// `java` a secas apunta a la extensión de Gradle dentro de este script, así que
// la clase se importa explícitamente.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma release con la MISMA keystore que la app de teléfono, si existe
// (`android/keystore.properties` del proyecto Capacitor). Ver BUILD-CAPACITOR.md.
val keystorePropsFile = rootProject.file("../android/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "cl.matchpoint.marcador.wear"
    compileSdk = 35

    defaultConfig {
        // Sufijo `.wear` para poder tener instaladas a la vez la app de teléfono
        // (Capacitor) y la del reloj durante el desarrollo. Si más adelante se
        // publica en Play con entrega multi-APK habría que unificar el applicationId.
        applicationId = "cl.matchpoint.marcador.wear"
        // Wear OS 3 en adelante. Los relojes con Wear OS 2 (API 28) quedan fuera.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    if (keystorePropsFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file("../android/${keystoreProps.getProperty("storeFile")}")
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // `viewModelScope` (cronómetro del partido) vive en -ktx, no en el artefacto base.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation("androidx.compose.ui:ui:1.8.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.2")

    // Wear Compose Material3 + foundation (rotary, pantalla redonda, TimeText).
    implementation("androidx.wear.compose:compose-material3:1.5.6")
    implementation("androidx.wear.compose:compose-foundation:1.5.6")
    implementation("androidx.wear.compose:compose-navigation:1.5.6")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")
    // `androidx.wear:wear` trae AmbientLifecycleObserver (Fase 4).
    implementation("androidx.wear:wear:1.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.8.2")

    testImplementation("junit:junit:4.13.2")
}
