plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}

allprojects {
    group = "com.agenticmessenger"
    version = "0.1.0-SNAPSHOT"
}
