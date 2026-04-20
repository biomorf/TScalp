package com.example.tscalp

import android.app.Application
import org.conscrypt.Conscrypt
import java.security.Security

class TScalpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Регистрируем Conscrypt при старте приложения
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }
}