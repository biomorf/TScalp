// Top-level build file
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    // Версия плагина не указывается отдельно, она будет взята из версии Kotlin
    //
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("org.jetbrains.kotlin.kapt") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

