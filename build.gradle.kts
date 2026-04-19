dependencies {
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Т-Инвестиции SDK
    implementation("ru.tinkoff.piapi:java-sdk-core:1.23")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // DI (можно Hilt или Koin, пока для простоты обойдёмся без них)
    // implementation("com.google.dagger:hilt-android:2.51.1")
    // kapt("com.google.dagger:hilt-compiler:2.51.1")
    
    // Хранение токена
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
