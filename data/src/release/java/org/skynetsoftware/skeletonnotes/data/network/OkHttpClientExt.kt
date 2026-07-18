package org.skynetsoftware.skeletonnotes.data.network

import okhttp3.OkHttpClient

fun OkHttpClient.Builder.setNextcloudTlsSocketFactory(): OkHttpClient.Builder = this
