package com.mandrecode.tempo.infrastructure.backup.drive

import com.mandrecode.tempo.features.backup.domain.model.BACKUP_FILE_PREFIX
import com.mandrecode.tempo.features.backup.domain.model.CloudBackupFile
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Backups in the app's hidden Drive folder (`appDataFolder`, scope `drive.appdata`, #304) through
 * the Drive v3 REST API. HTTP status maps onto what a user can act on: 401 and permission errors
 * are [DriveBackupFailure.AUTHORIZATION_NEEDED], a full quota is [DriveBackupFailure.STORAGE_FULL],
 * rate limits and server errors are [DriveBackupFailure.NETWORK] (worth retrying).
 */
class DriveRestCloudStore
    @Inject
    constructor(
        private val transport: HttpTransport,
    ) : CloudBackupStore {
        private val json = Json { ignoreUnknownKeys = true }

        /** Every page: retention must see all backups, however many accumulated while pruning failed. */
        override suspend fun list(accessToken: String): List<CloudBackupFile> {
            val query = "name contains '$NAME_PREFIX' and trashed = false"
            val base =
                "$API/files?spaces=appDataFolder&pageSize=$PAGE_SIZE&orderBy=createdTime%20desc" +
                    "&fields=${encode("nextPageToken,files($FIELDS)")}&q=${encode(query)}"
            val files = mutableListOf<CloudBackupFile>()
            var pageToken: String? = null
            do {
                val url = pageToken?.let { "$base&pageToken=${encode(it)}" } ?: base
                val page = json.decodeFromString<FileList>(call(HttpRequest("GET", url, auth(accessToken))).body)
                files += page.files.map { it.toDomain() }
                pageToken = page.nextPageToken
            } while (pageToken != null)
            return files
        }

        override suspend fun upload(
            accessToken: String,
            name: String,
            content: String,
        ): CloudBackupFile {
            val boundary = "tempo-${UUID.randomUUID()}"
            val metadata = """{"name":${json.encodeToString(name)},"parents":["appDataFolder"]}"""
            val body =
                buildString {
                    append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(metadata)
                    append("\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n")
                    append(content)
                    append("\r\n--$boundary--\r\n")
                }.toByteArray(Charsets.UTF_8)
            val url = "$UPLOAD_API/files?uploadType=multipart&fields=${encode(FIELDS)}"
            val headers = auth(accessToken) + ("Content-Type" to "multipart/related; boundary=$boundary")
            val response = call(HttpRequest("POST", url, headers, body))
            return json.decodeFromString<DriveFile>(response.body).toDomain()
        }

        override suspend fun download(
            accessToken: String,
            fileId: String,
        ): String = call(HttpRequest("GET", "$API/files/${encode(fileId)}?alt=media", auth(accessToken))).body

        override suspend fun delete(
            accessToken: String,
            fileId: String,
        ) {
            val response = transport.execute(HttpRequest("DELETE", "$API/files/${encode(fileId)}", auth(accessToken)))
            // Already gone is as good as deleted.
            if (response.code != HTTP_NOT_FOUND) response.requireSuccess()
        }

        private suspend fun call(request: HttpRequest): HttpResponse {
            val response = transport.execute(request)
            response.requireSuccess()
            return response
        }

        private fun HttpResponse.requireSuccess() {
            if (code in HTTP_OK until HTTP_MULTIPLE_CHOICES) return
            val failure =
                when {
                    code == HTTP_UNAUTHORIZED -> DriveBackupFailure.AUTHORIZATION_NEEDED
                    code == HTTP_FORBIDDEN && "storageQuotaExceeded" in body -> DriveBackupFailure.STORAGE_FULL
                    code == HTTP_FORBIDDEN && ("rateLimitExceeded" in body || "userRateLimitExceeded" in body) ->
                        DriveBackupFailure.NETWORK
                    code == HTTP_FORBIDDEN -> DriveBackupFailure.AUTHORIZATION_NEEDED
                    code == HTTP_TOO_MANY_REQUESTS || code >= HTTP_SERVER_ERROR -> DriveBackupFailure.NETWORK
                    else -> DriveBackupFailure.UNEXPECTED
                }
            throw CloudBackupException(failure, IllegalStateException("Drive responded $code"))
        }

        private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        @Serializable
        private data class FileList(
            val files: List<DriveFile> = emptyList(),
            val nextPageToken: String? = null,
        )

        @Serializable
        private data class DriveFile(
            val id: String,
            val name: String = "",
            val createdTime: String? = null,
            val size: String? = null,
        ) {
            fun toDomain() =
                CloudBackupFile(
                    id = id,
                    name = name,
                    createdAt = createdTime?.let { Instant.parse(it).toEpochMilliseconds() } ?: 0L,
                    sizeBytes = size?.toLongOrNull() ?: 0L,
                )
        }

        companion object {
            const val API = "https://www.googleapis.com/drive/v3"
            const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
            const val NAME_PREFIX = BACKUP_FILE_PREFIX
            private const val FIELDS = "id,name,createdTime,size"
            private const val PAGE_SIZE = 100
            private const val HTTP_OK = 200
            private const val HTTP_MULTIPLE_CHOICES = 300
            private const val HTTP_UNAUTHORIZED = 401
            private const val HTTP_FORBIDDEN = 403
            private const val HTTP_NOT_FOUND = 404
            private const val HTTP_TOO_MANY_REQUESTS = 429
            private const val HTTP_SERVER_ERROR = 500
        }
    }
