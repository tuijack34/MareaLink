// Espejo de Maven Central de Google: Maven Central a veces corta a los servidores de GitHub con "429 Too Many Requests"
val centralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

pluginManagement {
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri(centralMirror) }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "Marea"
include(":app")
