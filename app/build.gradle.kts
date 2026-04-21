plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "2.0.21"  //for navigation using sealed class
}

android {
    namespace = "com.example.tscalp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.tscalp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
            merges += setOf(
                "META-INF/services/javax.annotation.processing.Processor"
            )
        }
    }
}

// Блок для опциональной конфигурации компилятора Compose
composeCompiler {
    enableStrongSkippingMode = true
}



dependencies {
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    //implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    //implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.navigation:navigation-compose:2.8.0")    //for navigation using sealed classes
    //implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")    //for navigation using sealed classes

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Security for token storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // T-Invest API SDK
    //implementation("ru.tinkoff.piapi:java-sdk-core:1.23")
    // implementation("ru.tinkoff.piapi:java-sdk-core:1.7")
    // T-Invest API SDK. Исключаем стандартный Netty, который не работает на Android
    implementation("ru.tinkoff.piapi:java-sdk-core:1.7") {
        exclude(group = "io.grpc", module = "grpc-netty-shaded")
        exclude(group = "io.grpc", module = "grpc-netty")
    }

    // Замена для gRPC: OkHttp + AndroidChannelBuilder
    implementation("io.grpc:grpc-okhttp:1.57.2")
    implementation("io.grpc:grpc-stub:1.57.2")
    implementation("io.grpc:grpc-protobuf:1.57.2")
    implementation("io.grpc:grpc-android:1.57.2") // <-- Вот он, AndroidChannelBuilder

     // Conscrypt для решения проблем с SSL/TLS
    implementation("org.conscrypt:conscrypt-android:2.5.2")
}