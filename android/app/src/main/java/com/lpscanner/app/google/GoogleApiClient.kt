package com.lpscanner.app.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GoogleApiException(val code: Int, val googleMessage: String) : Exception("Google API error $code: $googleMessage") {
    /** The user unticked a permission on the consent screen, so the token lacks a scope. */
    val isMissingPermission: Boolean get() = code == 403 && "insufficient" in googleMessage.lowercase()
    val isNotFound: Boolean get() = code == 404
}

/**
 * Minimal authenticated JSON client for Google's REST APIs.
 * Sends the access token; on 401 it drops the token, gets a fresh one and retries once.
 */
class GoogleApiClient(
    private val tokens: TokenProvider,
    private val http: OkHttpClient = OkHttpClient(),
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true // spreadsheet cells may be JSON numbers; read them as strings
    }

    suspend fun <T> get(url: HttpUrl, deserializer: DeserializationStrategy<T>): T =
        json.decodeFromString(deserializer, execute { token -> Request.Builder().url(url).bearer(token).build() })

    suspend fun <T> post(url: HttpUrl, body: JsonElement, deserializer: DeserializationStrategy<T>): T {
        val bytes = json.encodeToString(JsonElement.serializer(), body)
        return json.decodeFromString(
            deserializer,
            execute { token -> Request.Builder().url(url).bearer(token).post(bytes.toRequestBody(JSON)).build() },
        )
    }

    /** POST where the response body doesn't matter. */
    suspend fun post(url: HttpUrl, body: JsonElement): JsonObject = post(url, body, JsonObject.serializer())

    private suspend fun execute(buildRequest: (token: String) -> Request): String {
        val token = tokens.token()
        val (code, body) = send(buildRequest(token))
        if (code != 401) return body.orThrow(code)

        tokens.invalidate(token)
        val (retryCode, retryBody) = send(buildRequest(tokens.token()))
        return retryBody.orThrow(retryCode)
    }

    private suspend fun send(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { it.code to it.body.string() }
    }

    private fun String.orThrow(code: Int): String =
        if (code in 200..299) this else throw GoogleApiException(code, errorMessage(this))

    /** Google errors look like {"error": {"code": 403, "message": "...", "status": "..."}}. */
    private fun errorMessage(body: String): String =
        runCatching { json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content }
            .getOrElse { body.take(300) }

    private fun Request.Builder.bearer(token: String) = header("Authorization", "Bearer $token")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
