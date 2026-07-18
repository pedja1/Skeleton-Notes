package org.skynetsoftware.skeletonnotes.data.network

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private val trustAllCerts: Array<TrustManager> =
    arrayOf(
        @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<X509Certificate?>?,
                authType: String?,
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<X509Certificate?>?,
                authType: String?,
            ) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate?> = emptyArray()
        },
    )

fun OkHttpClient.Builder.setNextcloudTlsSocketFactory(): OkHttpClient.Builder {
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, SecureRandom())
    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
    hostnameVerifier({ _, _ -> true })
    return this
}
