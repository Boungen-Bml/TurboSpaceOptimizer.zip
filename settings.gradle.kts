pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // required for Shizuku artifacts
    }
}

rootProject.name = "TurboSpaceOptimizer"
include(":app")
