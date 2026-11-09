plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.agenticmessenger.mcp.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.agenticmessenger.mcp.host"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(project(":amp-mcp"))
    // Local Maven until published (kotlin-mcp-android-binder: publishToMavenLocal).
    implementation("fi.italeino.mcp:binder-android:0.1.0-SNAPSHOT")
}
