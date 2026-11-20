package com.lpscanner.app.google

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GoogleApiClientTest {
    private val server = MockWebServer()

    /** Hands out "token-1", "token-2", ... and records invalidations. */
    private class FakeTokens : TokenProvider {
        var issued = 0
        val invalidated = mutableListOf<String>()
        private var current: String? = null
        override suspend fun token() = current ?: "token-${++issued}".also { current = it }
        override suspend fun invalidate(token: String) {
            invalidated += token
            if (current == token) current = null
        }
    }

    private val tokens = FakeTokens()
    private lateinit var drive: DriveApi

    @Before fun setUp() {
        server.start()
        drive = DriveApi(GoogleApiClient(tokens), baseUrl = server.url("/drive/v3/"))
    }

    @After fun tearDown() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    @Test fun `sends bearer token and parses the response`() = runTest {
        server.enqueue(json(200, """{"user":{"displayName":"Dana","emailAddress":"dana@example.com","kind":"x"}}"""))

        assertEquals(GoogleUser("Dana", "dana@example.com"), drive.currentUser())
        val request = server.takeRequest()
        assertEquals("Bearer token-1", request.headers["Authorization"])
        assertEquals("user(displayName,emailAddress)", request.url.queryParameter("fields"))
    }

    @Test fun `on 401 drops the token and retries once with a fresh one`() = runTest {
        server.enqueue(json(401, """{"error":"expired"}"""))
        server.enqueue(json(200, """{"user":{"emailAddress":"dana@example.com"}}"""))

        assertEquals("dana@example.com", drive.currentUser().emailAddress)
        assertEquals(listOf("token-1"), tokens.invalidated)
        assertEquals("Bearer token-1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer token-2", server.takeRequest().headers["Authorization"])
    }

    @Test fun `gives up after a second 401`() = runTest {
        server.enqueue(json(401, "{}"))
        server.enqueue(json(401, "{}"))
        try {
            drive.currentUser()
            fail("expected GoogleApiException")
        } catch (e: GoogleApiException) {
            assertEquals(401, e.code)
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun `surfaces other errors without retrying`() = runTest {
        server.enqueue(json(403, """{"error":{"message":"insufficient scopes"}}"""))
        try {
            drive.currentUser()
            fail("expected GoogleApiException")
        } catch (e: GoogleApiException) {
            assertEquals(403, e.code)
        }
        assertEquals(1, server.requestCount)
    }
}
