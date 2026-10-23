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
include(":amp-ui")
include(":amp-client-sdk")
include(":amp-agent")

val aidosAgentDir = listOfNotNull(
    System.getenv("AIDOS_AGENT_DIR")?.let { file(it) },
    file("../../aidos/integrate_aidos_agent_base/agent"),
    file("../aidos/agent"),
    file("D:/aidos/agent")
).firstOrNull { it.exists() && it.isDirectory }

if (aidosAgentDir != null) {
    includeBuild(aidosAgentDir) {
        dependencySubstitution {
            substitute(module("dev.aidos:agent-amp")).using(project(":amp"))
            substitute(module("dev.aidos:kernel")).using(project(":kernel"))
        }
    }
}
