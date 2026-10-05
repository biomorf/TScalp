import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appmetricaKey: String = localProps.getProperty("appmetrica.apiKey", "")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "2.3.21"  //for navigation using sealed class
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gitlab.biomorf.tscalp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gitlab.biomorf.tscalp"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1." + buildTime()   // теперь buildTime() вызывается на этапе конфигурации,
                                           // но при каждом новом запуске Gradle даст свежее время.
        buildConfigField("String", "APPMETRICA_API_KEY", "\"$appmetricaKey\"")
    }

    buildTypes {
            debug {
                //
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
            pickFirsts += listOf("**/com/google/protobuf/**")
        }
    }
}

// Теперь блок kotlin на верхнем уровне (вне android)
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
// Блок для опциональной конфигурации компилятора Compose
//composeCompiler {
//    enableStrongSkippingMode = true
//}



dependencies {
    // Core
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")    //for navigation using sealed classes

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // DataStore (замена SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    
    // Security for token storage
    implementation("androidx.security:security-crypto:1.1.0")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Unit-тесты
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.13")

    // AppMetrica SDK — crash reporting + analytics
    implementation("io.appmetrica.analytics:analytics:8.5.1")

    // Явно добавляем полную версию Protobuf, которая содержит GeneratedMessageV3
    implementation("com.google.protobuf:protobuf-java:3.25.8")
    implementation("com.google.protobuf:protobuf-kotlin:3.25.8")
    //implementation("com.google.protobuf:protobuf-java:4.34.1")

    // Исключаем конфликтующий модуль из всех конфигураций
    configurations.all {
        exclude(group = "com.google.api.grpc", module = "proto-google-common-protos")
    }

    // Исключаем все lite-версии из всех конфигураций
    configurations.all {
        exclude(group = "com.google.protobuf", module = "protobuf-lite")
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }

    //implementation("io.grpc:grpc-okhttp:1.68.1")
    //implementation("io.grpc:grpc-stub:1.68.1")
    //implementation("io.grpc:grpc-protobuf-lite:1.68.1")
    //implementation("io.grpc:grpc-netty:1.57.2") // явно добавим Netty без shaded
    implementation("io.grpc:grpc-okhttp:1.80.0")
    implementation("io.grpc:grpc-kotlin-stub:1.5.0")
    implementation("io.grpc:grpc-stub:1.80.0")
    implementation("io.grpc:grpc-protobuf-lite:1.80.0")
    implementation("io.grpc:grpc-netty:1.80.0") // явно добавим Netty без shaded

    // Обязательно для SSL на Android
    implementation("org.conscrypt:conscrypt-android:2.6.3")

    // Обязательная зависимость для ManagedChannel
    //implementation("io.grpc:grpc-stub:1.57.2")
    implementation("io.grpc:grpc-stub:1.80.0")

    // T-Invest API SDK
    implementation("ru.t-technologies.invest.piapi.kotlin:kotlin-sdk-grpc-core:1.51.0") {
        exclude(group = "io.grpc", module = "grpc-netty-shaded")
    }

    // BCS Broker
    //implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    //implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.code.gson:gson:2.13.2")

    //Finam API SDK

}

fun buildTime(): String {
    return LocalDateTime.now(ZoneId.of("UTC"))
        .format(DateTimeFormatter.ofPattern("yy.MM.dd.HHmm"))
}


// Одна задача для переименования debug APK (запускается после каждой сборки debug)
tasks.register("renameDebugApk") {
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val apkDir = File(buildDir, "outputs/apk/debug")
        apkDir.listFiles()?.filter { it.name.startsWith("tscalp-debug-") }?.forEach { it.delete() }
        val originalApk = File(apkDir, "app-debug.apk")
        if (originalApk.exists()) {
            val version = android.defaultConfig.versionName
            val newName = "tscalp-debug-v${version}.apk"
            val renamedApk = File(apkDir, newName)
            originalApk.copyTo(renamedApk)
            println("DEBUG APK переименован в: ${renamedApk.name}")
        } else {
            println("WARNING: app-debug.apk не найден по пути ${originalApk.absolutePath}")
        }
    }
}

afterEvaluate {
    tasks.named("assembleDebug") {
        finalizedBy("renameDebugApk")
    }
}

// Задача переименования Release APK
tasks.register("renameReleaseApk") {
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val apkDir = File(buildDir, "outputs/apk/release")
        apkDir.listFiles()?.filter { it.name.startsWith("tscalp-release-") }?.forEach { it.delete() }
        val originalApk = File(apkDir, "app-release.apk")
        if (originalApk.exists()) {
            val version = android.defaultConfig.versionName
            val newName = "tscalp-release-v${version}.apk"
            val renamedApk = File(apkDir, newName)
            originalApk.copyTo(renamedApk)
            println("RELEASE APK переименован в: ${renamedApk.name}")
        } else {
            println("WARNING: app-release.apk не найден по пути ${originalApk.absolutePath}")
        }
    }
}

afterEvaluate {
    tasks.named("assembleRelease") {
        finalizedBy("renameReleaseApk")
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

val copyDebugApk by tasks.registering(Copy::class) {
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")
    val version = android.defaultConfig.versionName

    from(apkDir.map { it.file("app-debug.apk") })
    into(apkDir)
    rename { "tscalp-debug-v${version}.apk" }
    dependsOn("assembleDebug")
}

val copyReleaseApk by tasks.registering(Copy::class) {
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val version = android.defaultConfig.versionName

    from(apkDir.map { it.file("app-release.apk") })
    into(apkDir)
    rename { "tscalp-release-v${version}.apk" }
    dependsOn("assembleRelease")
}
