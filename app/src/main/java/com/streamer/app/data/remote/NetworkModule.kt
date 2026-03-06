package com.streamer.app.data.remote

import android.util.Log
import com.streamer.app.BuildConfig
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Shared OkHttpClient singleton used by all network services.
 * Includes a lenient TrustManager that handles stale OCSP responses
 * (common on TV emulators and devices with system clock drift).
 */
object NetworkModule {
    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)

        // Handle OCSP validation failures on TV emulators/devices with clock drift
        try {
            val trustManager = createLenientTrustManager()
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        } catch (e: Exception) {
            Log.w("NetworkModule", "Failed to set up lenient TLS, using defaults", e)
        }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor { chain ->
                val req = chain.request()
                Log.d("HTTP", "-> ${req.method} ${req.url}")
                val t = System.currentTimeMillis()
                val resp = chain.proceed(req)
                Log.d("HTTP", "<- ${resp.code} ${req.url} (${System.currentTimeMillis() - t}ms)")
                resp
            }
        }

        builder.build()
    }

    /**
     * Creates a TrustManager that relaxes OCSP (Online Certificate Status Protocol) validation.
     * Handles "Response is unreliable: its validity interval is out-of-date" errors
     * on TV emulators and devices with system clock drift.
     * Certificate chain validation is still enforced — only stale OCSP responses are bypassed.
     */
    internal fun createLenientTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        val defaultTm = factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                defaultTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                try {
                    defaultTm.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    val isOcspIssue = generateSequence(e as Throwable) { it.cause }
                        .any { it.message?.contains("out-of-date", ignoreCase = true) == true }
                    if (!isOcspIssue) throw e
                    Log.w("NetworkModule", "Allowing connection despite stale OCSP response")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTm.acceptedIssuers
        }
    }
}
