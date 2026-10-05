package com.gitlab.biomorf.tscalp.data.api

import android.content.Context
import com.gitlab.biomorf.tscalp.R
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class CustomTrustManager(context: Context) : X509TrustManager {

    private val systemTrustManager: X509TrustManager
    private val customTrustManager: X509TrustManager

    init {
        val systemTmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        systemTmf.init(null as KeyStore?)
        systemTrustManager = systemTmf.trustManagers
            .first { it is X509TrustManager } as X509TrustManager

        // На Android используем "PKCS12", так как "JKS" недоступен
        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        loadCertificateToKeyStore(context, R.raw.russian_trusted_root_ca, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_root_ca_gost_2025, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_sub_ca, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_sub_ca_2024, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_sub_ca_gost_2025, keyStore)

        val customTmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        customTmf.init(keyStore)
        customTrustManager = customTmf.trustManagers
            .first { it is X509TrustManager } as X509TrustManager
    }

    private fun loadCertificateToKeyStore(context: Context, resId: Int, keyStore: KeyStore) {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            context.resources.openRawResource(resId).use { stream ->
                val cert = cf.generateCertificate(stream) as X509Certificate
                keyStore.setCertificateEntry("cert_$resId", cert)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            customTrustManager.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return systemTrustManager.acceptedIssuers + customTrustManager.acceptedIssuers
    }
}