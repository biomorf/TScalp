package com.example.tscalp

import android.app.Application
import android.util.Log
import java.security.Security
import com.example.tscalp.data.api.TinkoffInvestService

class TScalpApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Пробуем восстановить подключение при старте
        //val apiService = TinkoffInvestService(this)
        //val restored = apiService.tryInitializeFromStorage()

        //if (restored) {
        //    Log.d("TScalpApplication", "API успешно восстановлен из хранилища")
        //} else {
        //    Log.d("TScalpApplication", "API не был восстановлен (токен не найден)")
        //}

        // Устанавливаем Conscrypt как провайдера безопасности на старте
        //try {
        //    Security.insertProviderAt(Conscrypt.newProvider(), 1)
        //    Log.d("TScalpApplication", "Conscrypt установлен")
        //} catch (e: Exception) {
        //    Log.w("TScalpApplication", "Conscrypt уже установлен или не поддерживается")
        //}

        // Устанавливаем системные свойства для Netty
        System.setProperty("io.netty.handler.ssl.noOpenSsl", "false")
        System.setProperty("io.netty.tryReflectionSetAccessible", "true")
    }
}