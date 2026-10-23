plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val ktorVersion = "3.0.3"

dependencies {
    implementation(project(":amp-core"))
    implementation(project(":amp-crypto"))

    // Ktor Client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Database for Outbox & Local Working Memory
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Coroutines & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation(project(":amp-server"))
    testImplementation("ch.qos.logback:logback-classic:1.5.16")
}

tasks.test {
    useJUnitPlatform()
}
