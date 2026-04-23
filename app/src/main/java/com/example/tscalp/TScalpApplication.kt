package com.example.tscalp

import android.app.Application
import com.example.tscalp.di.ServiceLocator
import org.conscrypt.Conscrypt
import java.security.Security

class TScalpApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Регистрируем Conscrypt для работы SSL на Android
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        ServiceLocator.init(this)
        // Пытаемся восстановить подключение при старте
        ServiceLocator.tryRestoreApi()
    }
}