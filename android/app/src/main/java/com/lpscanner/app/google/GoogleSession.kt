package com.lpscanner.app.google

import android.app.PendingIntent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Supplies access tokens to API calls. Separate interface so API code is unit-testable. */
interface TokenProvider {
    suspend fun token(): String

    /** The API rejected [token] (expired/revoked): forget it so [token] fetches a new one. */
    suspend fun invalidate(token: String)
}

/** Thrown when a token can't be had silently; the UI must show Google's consent screen. */
class ConsentRequiredException(val pendingIntent: PendingIntent) :
    Exception("Google needs you to sign in again")

/** The user's grant lacks a permission the app needs (unticked on the consent screen, or removed later). */
class PermissionsMissingException : Exception("Google didn't grant all the permissions the app needs")

/** Caches the current access token in memory and renews it through [GoogleAuth]. */
class GoogleSession(private val auth: GoogleAuth) : TokenProvider {
    private val mutex = Mutex()
    private var cached: String? = null

    override suspend fun token(): String = mutex.withLock {
        cached ?: when (val outcome = auth.authorize()) {
            is GoogleAuth.Outcome.Authorized -> outcome.accessToken.also { cached = it }
            is GoogleAuth.Outcome.NeedsConsent -> throw ConsentRequiredException(outcome.pendingIntent)
            is GoogleAuth.Outcome.MissingScopes -> throw PermissionsMissingException()
        }
    }

    /** Forgets the cached token without revoking the grant (e.g. after a partial grant). */
    suspend fun forget() {
        val token = mutex.withLock { cached.also { cached = null } }
        token?.let { auth.clearToken(it) }
    }

    override suspend fun invalidate(token: String) {
        mutex.withLock { if (cached == token) cached = null }
        auth.clearToken(token)
    }

    /** Stores a token obtained from the consent screen. */
    suspend fun accept(token: String) = mutex.withLock { cached = token }

    suspend fun signOut(email: String) {
        val token = mutex.withLock { cached.also { cached = null } }
        token?.let { auth.clearToken(it) }
        auth.revoke(email)
    }
}
