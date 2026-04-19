// Top-level build file
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Версия плагина не указывается отдельно, она будет взята из версии Kotlin
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}