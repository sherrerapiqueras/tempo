package com.mandrecode.tempo.infrastructure.backup.drive

import com.google.common.truth.Truth.assertThat
import com.mandrecode.tempo.features.backup.domain.model.DriveBackupFailure
import com.mandrecode.tempo.features.backup.domain.repository.CloudBackupException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class DriveRestCloudStoreTest {
    private val requests = mutableListOf<HttpRequest>()
    private var response = HttpResponse(200, "{}")
    private var failWith: IOException? = null
    private val store =
        DriveRestCloudStore { request ->
            requests += request
            failWith?.let { throw it }
            response
        }

    @Test
    fun `list asks for this app's backups in the hidden folder, newest first`() =
        runTest {
            response =
                HttpResponse(
                    200,
                    """{"files":[{"id":"a","name":"tempo-backup-2.tempo",""" +
                        """"createdTime":"2026-11-17T10:00:00.000Z","size":"1234"}]}""",
                )

            val files = store.list("tok")

            val request = requests.single()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.url).startsWith("${DriveRestCloudStore.API}/files?spaces=appDataFolder")
            assertThat(request.url).contains("orderBy=createdTime%20desc")
            assertThat(request.url).contains("tempo-backup-")
            assertThat(request.headers["Authorization"]).isEqualTo("Bearer tok")
            assertThat(files.single().id).isEqualTo("a")
            assertThat(files.single().sizeBytes).isEqualTo(1234)
            assertThat(files.single().createdAt).isEqualTo(1_794_909_600_000L)
        }

    @Test
    fun `upload sends multipart metadata into appDataFolder followed by the content`() =
        runTest {
            response = HttpResponse(200, """{"id":"new","name":"tempo-backup-9.tempo","size":"7"}""")

            val file = store.upload("tok", "tempo-backup-9.tempo", "payload")

            val request = requests.single()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url).startsWith("${DriveRestCloudStore.UPLOAD_API}/files?uploadType=multipart")
            val contentType = request.headers.getValue("Content-Type")
            assertThat(contentType).startsWith("multipart/related; boundary=")
            val body = request.body!!.toString(Charsets.UTF_8)
            assertThat(body).contains(""""parents":["appDataFolder"]""")
            assertThat(body).contains(""""name":"tempo-backup-9.tempo"""")
            assertThat(body).contains("\r\n\r\npayload\r\n")
            assertThat(file.id).isEqualTo("new")
        }

    @Test
    fun `download reads the file content`() =
        runTest {
            response = HttpResponse(200, "content")

            assertThat(store.download("tok", "abc")).isEqualTo("content")
            assertThat(requests.single().url).endsWith("/files/abc?alt=media")
        }

    @Test
    fun `deleting a file that is already gone succeeds`() =
        runTest {
            response = HttpResponse(404, "")

            store.delete("tok", "gone")

            assertThat(requests.single().method).isEqualTo("DELETE")
        }

    @Test
    fun `status codes map to the failure a user can act on`() {
        fun failureFor(
            code: Int,
            body: String = "",
        ): DriveBackupFailure {
            response = HttpResponse(code, body)
            return assertThrows(CloudBackupException::class.java) { runTest { store.list("tok") } }.failure
        }

        assertThat(failureFor(401)).isEqualTo(DriveBackupFailure.AUTHORIZATION_NEEDED)
        assertThat(failureFor(403, """{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}"""))
            .isEqualTo(DriveBackupFailure.STORAGE_FULL)
        assertThat(failureFor(403, """{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}"""))
            .isEqualTo(DriveBackupFailure.NETWORK)
        assertThat(failureFor(403, """{"error":{"errors":[{"reason":"insufficientPermissions"}]}}"""))
            .isEqualTo(DriveBackupFailure.AUTHORIZATION_NEEDED)
        assertThat(failureFor(429)).isEqualTo(DriveBackupFailure.NETWORK)
        assertThat(failureFor(503)).isEqualTo(DriveBackupFailure.NETWORK)
        assertThat(failureFor(400)).isEqualTo(DriveBackupFailure.UNEXPECTED)
    }

    @Test
    fun `no response at all surfaces as an IOException`() {
        failWith = IOException("offline")

        assertThrows(IOException::class.java) { runTest { store.list("tok") } }
    }
}
