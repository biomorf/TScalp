// Top-level build file
plugins {
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    // Версия плагина не указывается отдельно, она будет взята из версии Kotlin
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    kotlin("plugin.serialization") version "2.3.21"
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("org.jetbrains.kotlin.kapt") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.4" apply false
}

