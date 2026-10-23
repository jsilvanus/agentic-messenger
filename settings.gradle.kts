pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "agentic-messenger"

include(":amp-core")
include(":amp-crypto")
include(":amp-server")
include(":amp-client-sdk")
