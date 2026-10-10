import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appmetricaKey: String = localProps.getProperty("appmetrica.apiKey", "")

// Keystore для подписи release. Файл не в git; в CI генерируется job'ом.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning: Boolean = keystoreProps.getProperty("storeFile") != null

// versionName для релиза — из -PreleaseVersionName=1.0.0 (CI).
// Локально null → debug и экспериментальные release получают timestamp-версию.
val releaseVersionName: String? = project.findProperty("releaseVersionName") as String?

// versionCode из semver: 1.2.3 → 10203. Для сборок без явной версии — 1.
val computedVersionCode: Int = releaseVersionName
    ?.split(".")
    ?.takeIf { it.size == 3 }
    ?.let { (major, minor, patch) ->
        (major.toIntOrNull() ?: 0) * 10_000 +
                (minor.toIntOrNull() ?: 0) * 100 +
                (patch.toIntOrNull() ?: 0)
    }
    ?: 1

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "2.4.20"  //for navigation using sealed class
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlinx.kover")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.gitlab.biomorf.tscalp"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.gitlab.biomorf.tscalp"
        minSdk = 30
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = computedVersionCode
        // теперь buildTime() вызывается на этапе конфигурации,
        // но при каждом новом запуске Gradle даст свежее время.
        versionName = releaseVersionName ?: ("${lastReleaseVersion()}-dev." + buildTime())
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
                if (hasReleaseSigning) {
                    signingConfig = signingConfigs.getByName("release")
                }
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

    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = false
        checkReleaseBuilds = false
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable"
        )
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
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")
    implementation("androidx.navigation:navigation-compose:2.10.2") //for navigation using sealed classes

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
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
    implementation("io.grpc:grpc-okhttp:1.84.1")
    implementation("io.grpc:grpc-kotlin-stub:1.5.0")
    implementation("io.grpc:grpc-stub:1.84.1")
    implementation("io.grpc:grpc-protobuf-lite:1.84.1")
    implementation("io.grpc:grpc-netty:1.84.1") // явно добавим Netty без shaded

    // Обязательно для SSL на Android
    implementation("org.conscrypt:conscrypt-android:2.6.3")

    // Обязательная зависимость для ManagedChannel
    //implementation("io.grpc:grpc-stub:1.57.2")
    //implementation("io.grpc:grpc-stub:1.84.1")

    // T-Invest API SDK
    implementation("ru.t-technologies.invest.piapi.kotlin:kotlin-sdk-grpc-core:1.51.0") {
        exclude(group = "io.grpc", module = "grpc-netty-shaded")
    }

    // BCS Broker
    //implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    //implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.code.gson:gson:2.14.0")

    //Finam API SDK

}

fun buildTime(): String {
    return LocalDateTime.now(ZoneId.of("UTC"))
        .format(DateTimeFormatter.ofPattern("yy.MM.dd.HHmm"))
}

// Последний релизный тег (vX.Y.Z) из локального git.
// Fallback — "0.0.0", если тегов нет или git недоступен
// (shallow clone без GIT_DEPTH: 0, отсутствие git в PATH).
fun lastReleaseVersion(): String {
    return try {
        val process = ProcessBuilder("git", "tag", "--sort=-v:refname", "--list", "v*")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.removePrefix("v")
            ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }
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

val copyDebugApk = tasks.register<Copy>("copyDebugApk") {
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")
    val version = android.defaultConfig.versionName

    from(apkDir.map { it.file("app-debug.apk") })
    into(apkDir)
    rename { "tscalp-debug-v${version}.apk" }
    dependsOn("assembleDebug")
}

val copyReleaseApk = tasks.register<Copy>("copyReleaseApk") {
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val version = android.defaultConfig.versionName

    from(apkDir.map { it.file("app-release.apk") })
    into(apkDir)
    rename { "tscalp-release-v${version}.apk" }
    dependsOn("assembleRelease")
}

kover {
    reports {
        filters {
            excludes {
                androidGeneratedClasses()
                classes(
                    "*Fragment",
                    "*Fragment\$*",
                    "*Activity",
                    "*Activity\$*",
                    "*ComposableSingletons*",
                    "*_Factory",
                    "*_HiltModules*",
                    "*_MembersInjector",
                    "*_GeneratedInjector",
                    "*Hilt_*",
                    "dagger.hilt.internal.aggregatedroot.codegen.*",
                    "hilt_aggregated_deps.*",
                    "*Dagger*",
                    "*BuildConfig",
                    "*Compose*"
                )
            }
        }
        verify {
            rule {
                minBound(3)
            }
        }
    }
}

