package com.example.tscalp

import android.app.Application
import com.example.tscalp.di.ServiceLocator

class TScalpApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ServiceLocator.init(this)
        // Пытаемся восстановить подключение при старте
        ServiceLocator.tryRestoreApi()
    }
}