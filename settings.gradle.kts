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
        // The MCP Binder library, published from kotlin-mcp-android-binder on every push to main.
        // Pinned by mcpBinderVersion in gradle.properties. Reads need a token: gpr.user/gpr.key on a
        // developer machine, GITHUB_ACTOR/GITHUB_TOKEN in CI.
        maven {
            name = "McpBinderGitHubPackages"
            url = uri("https://maven.pkg.github.com/jsilvanus/kotlin-mcp-android-binder")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull ?: "token"
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull ?: ""
            }
            content { includeGroup("fi.italeino.mcp") }
        }
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

// Opt-in composite build of a sibling kotlin-mcp-android-binder checkout (work on the binder and
// this repo at once). Gradle substitutes the fi.italeino.mcp coordinates with the local projects.
providers.gradleProperty("mcpBinderDir").orNull?.let { includeBuild(it) }
