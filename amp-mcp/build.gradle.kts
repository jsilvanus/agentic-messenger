plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":amp-agent"))
    // The official Kotlin MCP SDK; the same version Aidos's own MCP client uses.
    api("io.modelcontextprotocol:kotlin-sdk-server:0.15.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.modelcontextprotocol:kotlin-sdk-client:0.15.0")
    testImplementation("io.modelcontextprotocol:kotlin-sdk-testing:0.15.0")
}

tasks.test {
    useJUnitPlatform()
}
