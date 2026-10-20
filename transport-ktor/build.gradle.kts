plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    kotlin("plugin.serialization")
}

kotlin {
    android {
        namespace = "com.gitlab.biomorf.tscalp.transport.ktor"
        compileSdk = 37
        minSdk = 30

        withHostTest {}

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Ktor Client — ядро и WebSocket
                implementation("io.ktor:ktor-client-core:3.6.0")
                implementation("io.ktor:ktor-client-websockets:3.6.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
                // Coroutines и Flow
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
        val androidMain by getting {
            dependencies {
                // OkHttp engine для Android
                implementation("io.ktor:ktor-client-okhttp:3.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
    }
}
