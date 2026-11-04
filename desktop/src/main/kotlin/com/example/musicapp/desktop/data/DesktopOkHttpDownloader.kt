package com.example.musicapp.desktop.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class DesktopOkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")

        request.headers().forEach { (key, list) ->
            for (value in list) {
                builder.header(key, value)
            }
        }

        if (request.httpMethod() == "POST") {
            builder.post((request.dataToSend() ?: ByteArray(0)).toRequestBody(null))
        } else if (request.httpMethod() == "GET") {
            builder.get()
        }

        val okHttpResponse = client.newCall(builder.build()).execute()

        val responseHeaders = mutableMapOf<String, List<String>>()
        okHttpResponse.headers.names().forEach { name ->
            responseHeaders[name] = okHttpResponse.headers.values(name)
        }

        val bodyStr = okHttpResponse.body?.string().orEmpty()

        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            responseHeaders,
            bodyStr,
            request.url()
        )
    }
}
