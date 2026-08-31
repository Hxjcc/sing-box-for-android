package io.nekohasekai.sfa.utils

import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.ktx.unwrap
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class HTTPClient : Closeable {
    data class Response(
        val content: String,
        val headers: Map<String, List<String>>?,
    ) {
        fun header(name: String): String? = headers
            ?.entries
            ?.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
    }

    companion object {
        private const val PLATFORM_CONNECT_TIMEOUT_MILLIS = 15_000
        private const val PLATFORM_READ_TIMEOUT_MILLIS = 30_000

        val userAgent by lazy {
            var userAgent = "SFA (sing-box "
            userAgent += Libbox.version()
            userAgent += "; language "
            userAgent += Locale.getDefault().toLanguageTag().replace("-", "_")
            userAgent += ")"
            userAgent
        }
    }

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        val request = client.newRequest()
        request.setUserAgent(userAgent)
        request.setURL(url)
        val response = request.execute()
        return response.content.unwrap
    }

    /**
     * Fetches the response body and headers in one request. The libbox HTTP response API does not
     * currently expose headers, so fall back to it when the platform connection cannot be used.
     * A null headers map indicates that only the body could be fetched.
     */
    fun getStringWithHeaders(url: String): Response = try {
        getStringWithPlatformConnection(url)
    } catch (_: Exception) {
        Response(content = getString(url), headers = null)
    }

    private fun getStringWithPlatformConnection(url: String): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = PLATFORM_CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = PLATFORM_READ_TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", userAgent)

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            val content = connection.inputStream.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
            val headers = buildMap {
                connection.headerFields.forEach { (name, values) ->
                    if (name != null && values != null) put(name, values)
                }
            }
            return Response(content = content, headers = headers)
        } finally {
            connection.disconnect()
        }
    }

    override fun close() {
        client.close()
    }
}
