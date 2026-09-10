// Versiones: Wear Compose 1.5.x exige compileSdk 35 y AGP >= 8.6 (leído del
// aar-metadata). AGP 8.7.x pide Gradle 8.9. JDK 17 de esta máquina alcanza.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
