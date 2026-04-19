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
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    //implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    //implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.navigation:navigation-compose:2.8.0")    //for navigation using sealed classes
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")    //for navigation using sealed classes
    
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    
    // Т-Инвестиции SDK
    implementation("ru.tinkoff.piapi:java-sdk-core:1.23")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Security for token storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}