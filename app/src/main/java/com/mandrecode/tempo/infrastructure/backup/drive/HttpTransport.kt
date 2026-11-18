package com.mandrecode.tempo.infrastructure.backup.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class HttpResponse(
    val code: Int,
    val body: String,
)

/** The minimum HTTP the Drive client needs, behind an interface so it can be tested without a network. */
fun interface HttpTransport {
    /** Throws [java.io.IOException] when there is no response at all. */
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** [HttpTransport] on the platform's [HttpURLConnection]: four Drive calls do not justify an HTTP library. */
class UrlConnectionTransport
    @Inject
    constructor() : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse =
            withContext(Dispatchers.IO) {
                val connection = URL(request.url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = request.method
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                    request.body?.let { body ->
                        connection.doOutput = true
                        connection.setFixedLengthStreamingMode(body.size)
                        connection.outputStream.use { it.write(body) }
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
                } finally {
                    connection.disconnect()
                }
            }

        private companion object {
            const val TIMEOUT_MS = 60_000
        }
    }
