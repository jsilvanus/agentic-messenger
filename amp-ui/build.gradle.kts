plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

dependencies {
    implementation(project(":amp-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Compose Multiplatform / Desktop / Material3
    implementation("org.jetbrains.compose.runtime:runtime:1.6.11")
    implementation("org.jetbrains.compose.foundation:foundation:1.6.11")
    implementation("org.jetbrains.compose.material3:material3:1.6.11")
    implementation("org.jetbrains.compose.ui:ui:1.6.11")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.test {
    useJUnitPlatform()
}
