package com.example.security

import android.content.Context
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Custom TrustManager that enforces Trust-On-First-Use (TOFU) pinning
 * exclusively for host 172.16.16.16 (the Sophos user portal).
 * Does NOT disable certificate checking globally.
 */
class SelfSignedCertManager(private val context: Context) {
    companion object {
        private const val PREFS_NAME = "campus_wifi_certs"
        private const val KEY_PINNED_CERT_SHA256 = "pinned_172_16_16_16_sha256"
        const val TRUSTED_HOST = "172.16.16.16"
        const val USER_PORTAL_URL = "https://172.16.16.16:4443/userportal/webpages/myaccount/login.jsp"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPinnedSha256(): String? = prefs.getString(KEY_PINNED_CERT_SHA256, null)

    fun clearPinnedCert() {
        prefs.edit().remove(KEY_PINNED_CERT_SHA256).apply()
    }

    fun createPinnedSslSocketFactory(): SSLSocketFactory {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) {
                    throw CertificateException("Empty certificate chain")
                }
                val cert = chain[0]
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(cert.encoded)
                val hexString = digest.joinToString("") { "%02X".format(it) }

                val pinned = prefs.getString(KEY_PINNED_CERT_SHA256, null)
                if (pinned == null) {
                    // Trust on first use (TOFU): pin this cert's fingerprint
                    prefs.edit().putString(KEY_PINNED_CERT_SHA256, hexString).apply()
                } else if (pinned != hexString) {
                    throw CertificateException(
                        "Certificate fingerprint mismatch for $TRUSTED_HOST! Expected $pinned but received $hexString"
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
        return sslContext.socketFactory
    }

    fun configureHttpsConnectionForPortal(connection: HttpsURLConnection) {
        if (connection.url.host == TRUSTED_HOST) {
            connection.sslSocketFactory = createPinnedSslSocketFactory()
            connection.setHostnameVerifier { hostname, _ ->
                hostname == TRUSTED_HOST
            }
        }
    }
}
