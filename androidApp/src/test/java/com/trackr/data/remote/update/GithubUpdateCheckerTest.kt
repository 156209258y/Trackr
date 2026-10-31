package com.trackr.data.remote.update

import com.trackr.core.model.UpdateCheckResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class GithubUpdateCheckerTest {

    @Test
    fun checkForUpdates_whenNewVersionAvailable_returnsHasUpdate() = runTest {
        val jsonPayload = """
            {
                "versionCode": 3,
                "versionName": "0.0.3",
                "minSupportedVersionCode": 1,
                "changelog": "新特性 1\n新特性 2",
                "downloadUrl": "https://github.com/5151561/Trackr/releases/tag/v0.0.3",
                "publishTime": "2026-10-30",
                "forceUpdate": false
            }
        """.trimIndent()

        val client = clientReturning(jsonPayload)

        val checker = GithubUpdateChecker(httpClient = client, endpointUrls = listOf("https://example.com/update.json"))
        val result = checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2")

        assertTrue(result is UpdateCheckResult.HasUpdate)
        val hasUpdate = result as UpdateCheckResult.HasUpdate
        assertEquals(3, hasUpdate.updateInfo.versionCode)
        assertEquals("0.0.3", hasUpdate.updateInfo.versionName)
        assertEquals("新特性 1\n新特性 2", hasUpdate.updateInfo.changelog)
        assertEquals("https://github.com/5151561/Trackr/releases/tag/v0.0.3", hasUpdate.updateInfo.downloadUrl)
        assertFalse(hasUpdate.isForceUpdate)
    }

    @Test
    fun checkForUpdates_whenAlreadyLatestVersion_returnsUpToDate() = runTest {
        val jsonPayload = """
            {
                "versionCode": 2,
                "versionName": "0.0.2",
                "minSupportedVersionCode": 1,
                "changelog": "更新说明",
                "downloadUrl": "https://github.com/5151561/Trackr/releases",
                "publishTime": "2026-10-30",
                "forceUpdate": false
            }
        """.trimIndent()

        val client = clientReturning(jsonPayload)

        val checker = GithubUpdateChecker(httpClient = client, endpointUrls = listOf("https://example.com/update.json"))
        val result = checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2")

        assertTrue(result is UpdateCheckResult.UpToDate)
        assertEquals("0.0.2", (result as UpdateCheckResult.UpToDate).currentVersionName)
    }

    @Test
    fun checkForUpdates_whenVersionLowerThanMinSupported_marksForceUpdate() = runTest {
        val jsonPayload = """
            {
                "versionCode": 10,
                "versionName": "1.0.0",
                "minSupportedVersionCode": 5,
                "changelog": "大版本升级",
                "downloadUrl": "https://github.com/5151561/Trackr/releases",
                "forceUpdate": false
            }
        """.trimIndent()

        val client = clientReturning(jsonPayload)

        val checker = GithubUpdateChecker(httpClient = client, endpointUrls = listOf("https://example.com/update.json"))
        val result = checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2")

        assertTrue(result is UpdateCheckResult.HasUpdate)
        assertTrue((result as UpdateCheckResult.HasUpdate).isForceUpdate)
    }

    @Test
    fun checkForUpdates_whenFirstEndpointFails_fallsBackToNextEndpoint() = runTest {
        val jsonPayload = """
            {
                "versionCode": 3,
                "versionName": "0.0.3",
                "changelog": "Fallback successful",
                "downloadUrl": "https://github.com/5151561/Trackr/releases"
            }
        """.trimIndent()

        val client = HttpClient(MockEngine { request ->
            if (request.url.toString().contains("primary.com")) {
                throw IOException("Network timeout on primary")
            } else {
                respondJson(jsonPayload)
            }
        })

        val checker = GithubUpdateChecker(
            httpClient = client,
            endpointUrls = listOf("https://primary.com/update.json", "https://cdn.example.com/update.json")
        )
        val result = checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2")

        assertTrue(result is UpdateCheckResult.HasUpdate)
        assertEquals("0.0.3", (result as UpdateCheckResult.HasUpdate).updateInfo.versionName)
    }

    @Test
    fun checkForUpdates_whenAllEndpointsFail_returnsError() = runTest {
        val client = HttpClient(MockEngine { _ -> throw IOException("Unable to resolve host") })

        val checker = GithubUpdateChecker(
            httpClient = client,
            endpointUrls = listOf("https://bad1.com/update.json", "https://bad2.com/update.json")
        )
        val result = checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2")

        assertTrue(result is UpdateCheckResult.Error)
        assertEquals("Unable to resolve host", (result as UpdateCheckResult.Error).message)
    }

    private fun MockRequestHandleScope.respondJson(jsonPayload: String) =
        respond(jsonPayload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun clientReturning(jsonPayload: String): HttpClient =
        HttpClient(MockEngine { _ -> respondJson(jsonPayload) })

    private fun payloadWithDownloadUrl(downloadUrl: String) = """
        {
            "versionCode": 3,
            "versionName": "0.0.3",
            "changelog": "更新说明",
            "downloadUrl": "$downloadUrl"
        }
    """.trimIndent()

    @Test
    fun checkForUpdates_whenDownloadUrlIsHttp_returnsError() = runTest {
        val checker = GithubUpdateChecker(
            httpClient = clientReturning(payloadWithDownloadUrl("http://github.com/5151561/Trackr/releases")),
            endpointUrls = listOf("https://example.com/update.json")
        )
        val result = checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2")
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun checkForUpdates_whenDownloadHostNotWhitelisted_returnsError() = runTest {
        val checker = GithubUpdateChecker(
            httpClient = clientReturning(payloadWithDownloadUrl("https://evil.example.com/trackr.apk")),
            endpointUrls = listOf("https://example.com/update.json")
        )
        val result = checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2")
        assertTrue(result is UpdateCheckResult.Error)
    }

    @Test
    fun checkForUpdates_whenCancelled_doesNotTryRemainingEndpoints() = runBlocking {
        val requested = java.util.concurrent.atomic.AtomicInteger(0)
        val entered = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { _ ->
            requested.incrementAndGet()
            entered.complete(Unit)
            awaitCancellation()
        })
        val checker = GithubUpdateChecker(
            httpClient = client,
            endpointUrls = listOf("https://a.example.com/u.json", "https://b.example.com/u.json")
        )

        val job = launch(Dispatchers.Default) { checker.checkForUpdates(currentVersionCode = 2, currentVersionName = "0.0.2") }
        withTimeout(2_000L) { entered.await() }
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(1, requested.get())
    }

    @Test
    fun isAllowedDownloadUrl_validatesSchemeAndHost() {
        assertTrue(GithubUpdateChecker.isAllowedDownloadUrl("https://github.com/5151561/Trackr/releases"))
        assertTrue(GithubUpdateChecker.isAllowedDownloadUrl("https://objects.githubusercontent.com/a/b.apk"))
        assertTrue(GithubUpdateChecker.isAllowedDownloadUrl("https://raw.githubusercontent.com/a/b.apk"))
        assertTrue(GithubUpdateChecker.isAllowedDownloadUrl("https://cdn.jsdelivr.net/gh/a/b.apk"))
        assertTrue(GithubUpdateChecker.isAllowedDownloadUrl("https://fastly.jsdelivr.net/gh/a/b.apk"))
        assertTrue(GithubUpdateChecker.isAllowedDownloadUrl("https://GitHub.com/5151561/Trackr"))

        assertFalse(GithubUpdateChecker.isAllowedDownloadUrl("http://github.com/5151561/Trackr"))
        assertFalse(GithubUpdateChecker.isAllowedDownloadUrl("https://github.com.evil.com/a.apk"))
        assertFalse(GithubUpdateChecker.isAllowedDownloadUrl("https://evilgithub.com/a.apk"))
        assertFalse(GithubUpdateChecker.isAllowedDownloadUrl("https://user@evil.com/github.com"))
        assertFalse(GithubUpdateChecker.isAllowedDownloadUrl("ftp://github.com/a.apk"))
        assertFalse(GithubUpdateChecker.isAllowedDownloadUrl("javascript:alert(1)"))
        assertFalse(GithubUpdateChecker.isAllowedDownloadUrl(""))
    }
}
