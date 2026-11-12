package com.gitlab.biomorf.tscalp.data.api.common

import android.content.Context
import com.gitlab.biomorf.tscalp.R
import com.gitlab.biomorf.tscalp.domain.log.Logger
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * X509TrustManager for brokers using Russian Trusted Root CA
 * (T-Invest; potential for BCS / Finam if they switch to Russian CA).
 *
 * **Why custom, not Network Security Config (NNC).** NNC `<trust-anchors>`
 * is the "canonical" Android mechanism for adding a CA, but is
 * deliberately not used here. Two concrete reasons:
 *
 * 1. **Ban protection.** Google Play Store / Android OS can restrict
 *    NNC `<trust-anchors>` for non-standard CA (policy change,
 *    targetSdk bump, Play Integrity gates). If that happens, NNC
 *    would silently drop Russian CA and TLS to T-Invest would break
 *    on the next app update. Custom `X509TrustManager` is OS-policy-
 *    agnostic: `KeyStore` + `TrustManagerFactory` are stable public
 *    APIs with no equivalent restriction lever.
 * 2. **GOST 2025 / sub-CA 2024/2025 may not parse in NNC.** NNC uses
 *    the system `Conscrypt` XML parser; GOST chains / non-RSA CAs in
 *    `<trust-anchors>` have known gaps (Android 10+ rejects some).
 *    The custom path goes through `CertificateFactory` +
 *    `TrustManagerFactory`, which accept any valid X.509.
 *
 * **Trade-off.** Lint `CustomX509TrustManager` warning is present;
 * tracked in `lint-baseline.xml` (clean-audit Н81, accepted 2c). The
 * warning is generic ("custom trust manager may be insecure") and
 * does not analyse [checkServerTrusted]: it delegates to the system
 * trust manager first, and only falls back to the custom manager
 * (5 Russian CA) on `CertificateException`.
 *
 * **Fail-fast.** [loadCertificateToKeyStore] throws
 * `IllegalStateException` on any failure; the `init` block checks
 * `acceptedIssuers.size >= EXPECTED_CA_COUNT` (clean-audit Н79 / Н80).
 */
class CustomTrustManager(
    context: Context,
    private val logger: Logger,
) : X509TrustManager {
    private companion object {
        private const val TAG = "CustomTrustManager"
        private const val EXPECTED_CA_COUNT = 5
    }

    private val systemTrustManager: X509TrustManager
    private val customTrustManager: X509TrustManager

    init {
        val systemTmf =
            TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm(),
            )
        systemTmf.init(null as KeyStore?)
        systemTrustManager =
            systemTmf.trustManagers
                .first { it is X509TrustManager } as X509TrustManager

        // На Android используем "PKCS12", так как "JKS" недоступен
        val keyStore = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        loadCertificateToKeyStore(context, R.raw.russian_trusted_root_ca, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_root_ca_gost_2025, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_sub_ca, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_sub_ca_2024, keyStore)
        loadCertificateToKeyStore(context, R.raw.russian_trusted_sub_ca_gost_2025, keyStore)

        val customTmf =
            TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm(),
            )
        customTmf.init(keyStore)
        customTrustManager =
            customTmf.trustManagers
                .first { it is X509TrustManager } as X509TrustManager
        check(customTrustManager.acceptedIssuers.size >= EXPECTED_CA_COUNT) {
            "Expected $EXPECTED_CA_COUNT Russian CAs, got ${customTrustManager.acceptedIssuers.size}"
        }
    }

    private fun loadCertificateToKeyStore(
        context: Context,
        resId: Int,
        keyStore: KeyStore,
    ) {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            context.resources.openRawResource(resId).use { stream ->
                val cert = cf.generateCertificate(stream) as X509Certificate
                keyStore.setCertificateEntry("cert_$resId", cert)
            }
        } catch (e: Exception) {
            logger.w(TAG, "CA load failed: res=$resId", e)
            throw IllegalStateException("CA load failed: res=$resId", e)
        }
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        try {
            systemTrustManager.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            customTrustManager.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrustManager.acceptedIssuers + customTrustManager.acceptedIssuers
}
