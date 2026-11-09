plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
    id("com.android.application") version "8.5.2" apply false
    kotlin("android") version "2.4.10" apply false
}

allprojects {
    group = "com.agenticmessenger"
    version = "0.1.0-SNAPSHOT"
}

// The Android host (amp-mcp-android) dexes these JVM modules, and Android toolchains take Java 17
// bytecode reliably; a newer class-file version fails dexing. 17 runs unchanged on every JDK that builds this.
subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
    }
}
