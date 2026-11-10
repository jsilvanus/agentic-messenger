pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
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
include(":amp-mcp")
// The Android host for amp-mcp needs an Android SDK; plain JVM environments do not have one.
if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || (file("local.properties").exists() && file("local.properties").readText().contains("sdk.dir"))) {
    include(":amp-mcp-android")
}

val aidosAgentDir = listOfNotNull(
    System.getenv("AIDOS_AGENT_DIR")?.let { file(it) },
    file("../aidos/agent"),
    file("D:/aidos/agent"),
    file("../../aidos/project_status_roadmap_review/agent"),
    file("../../aidos/integrate_aidos_agent_base/agent")
).firstOrNull { it.exists() && it.isDirectory }

if (aidosAgentDir != null) {
    includeBuild(aidosAgentDir) {
        name = "aidos-agent"
        dependencySubstitution {
            substitute(module("dev.aidos:agent-amp")).using(project(":amp"))
            substitute(module("dev.aidos:kernel")).using(project(":kernel"))
        }
    }
}

val aidosTcrCandidates = listOf(
    file("../aidos-tcr"),
    file("D:/aidos-tcr"),
    file("c:/Users/jsilv/Code/aidos-tcr")
)
val aidosTcrDir = aidosTcrCandidates.firstOrNull { it.exists() && it.isDirectory }
if (aidosTcrDir != null) {
    includeBuild(aidosTcrDir)
}
