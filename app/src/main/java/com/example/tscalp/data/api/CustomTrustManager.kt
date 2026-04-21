package com.example.tscalp.data.api

import android.content.Context
import com.example.tscalp.R
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class CustomTrustManager(context: Context) : X509TrustManager {

    private val defaultTrustManager: X509TrustManager
    private val trustedCerts: MutableSet<X509Certificate> = mutableSetOf()

    init {
        // 1. Стандартный системный TrustManager
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        defaultTrustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager

        // 2. Загружаем все резервные корневые сертификаты из raw
        loadCertificate(context, R.raw.russian_trusted_root_ca)
        loadCertificate(context, R.raw.russian_trusted_root_ca_gost_2025)
        loadCertificate(context, R.raw.russian_trusted_sub_ca)
        loadCertificate(context, R.raw.russian_trusted_sub_ca_2024)
        loadCertificate(context, R.raw.russian_trusted_sub_ca_gost_2025)
        // пример второго сертификата
        // Добавьте сюда другие сертификаты по мере необходимости
    }

    private fun loadCertificate(context: Context, resId: Int) {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            context.resources.openRawResource(resId).use { stream ->
                val cert = cf.generateCertificate(stream) as X509Certificate
                trustedCerts.add(cert)
            }
        } catch (e: Exception) {
            // Логируем, но не прерываем инициализацию
            e.printStackTrace()
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        defaultTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            defaultTrustManager.checkServerTrusted(chain, authType)
        } catch (e: Exception) {
            // Пробуем проверить через любой из наших резервных сертификатов
            if (chain != null && chain.isNotEmpty()) {
                val serverCert = chain[0]
                for (trustedCert in trustedCerts) {
                    try {
                        serverCert.verify(trustedCert.publicKey)
                        // Если верификация прошла – доверяем
                        return
                    } catch (verifyException: Exception) {
                        // Пробуем следующий сертификат
                    }
                }
            }
            throw e
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return defaultTrustManager.acceptedIssuers + trustedCerts.toTypedArray()
    }
}