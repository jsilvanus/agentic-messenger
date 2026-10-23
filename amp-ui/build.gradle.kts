plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":amp-core"))
    implementation(project(":amp-crypto"))
    implementation(project(":amp-client-sdk"))
    implementation(project(":amp-agent"))
    implementation("dev.aidos:kernel")
    implementation("dev.aidos:agent-amp")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Compose Multiplatform / Desktop / Material3
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.runtime:runtime:1.6.11")
    implementation("org.jetbrains.compose.foundation:foundation:1.6.11")
    implementation("org.jetbrains.compose.material3:material3:1.6.11")
    implementation("org.jetbrains.compose.ui:ui:1.6.11")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation(project(":amp-server"))
    testImplementation("io.ktor:ktor-server-core:3.0.3")
    testImplementation("io.ktor:ktor-server-netty:3.0.3")
    testImplementation("io.ktor:ktor-server-websockets:3.0.3")
    testImplementation("ch.qos.logback:logback-classic:1.5.16")
}

compose.desktop {
    application {
        mainClass = "com.agenticmessenger.ui.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}
