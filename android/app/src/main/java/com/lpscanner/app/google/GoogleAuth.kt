package com.lpscanner.app.google

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/** OAuth scopes the app asks for. Keep in sync with the Cloud project's "Data access" page. */
object GoogleScopes {
    /** Read/write the user's spreadsheets (only ones their account can already access). */
    const val SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"

    /**
     * Drive access limited to files this app created (or the user opened with it): lists the
     * sheets made here and reads their sharing settings. Google doesn't treat it as sensitive.
     * Sheets opened from a link work through [SPREADSHEETS] alone.
     */
    const val DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"

    val ALL = listOf(SPREADSHEETS, DRIVE_FILE)
}

/**
 * Thin wrapper over Google Play services' AuthorizationClient.
 *
 * Play services identifies the app by package name + signing key (matched against the
 * Android OAuth client in the Cloud project), shows the consent screen when needed, and
 * hands back short-lived access tokens. It renews them silently once the user consented.
 */
class GoogleAuth(context: Context) {
    sealed interface Outcome {
        data class Authorized(val accessToken: String) : Outcome

        /** The user must act: pick an account and/or consent. Launch this intent. */
        data class NeedsConsent(val pendingIntent: PendingIntent) : Outcome

        /** The user signed in but unticked a permission on the consent screen. [token] is the partial grant. */
        data class MissingScopes(val token: String, val missing: List<String>) : Outcome
    }

    private val client = Identity.getAuthorizationClient(context)
    private val scopes = GoogleScopes.ALL.map(::Scope)

    /** Returns a token silently if the user already consented; otherwise asks for UI. */
    suspend fun authorize(): Outcome =
        client.authorize(AuthorizationRequest.builder().setRequestedScopes(scopes).build()).await().toOutcome()

    /** Reads the result of the consent screen launched from [Outcome.NeedsConsent]. */
    fun outcomeFromConsentIntent(data: Intent?): Outcome = client.getAuthorizationResultFromIntent(data).toOutcome()

    /** Drops a token from Play services' cache (e.g. after the API rejected it). */
    suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }

    /** Signs out for real: removes the app's grant, so the next sign-in asks again. */
    suspend fun revoke(email: String) {
        client.revokeAccess(
            RevokeAccessRequest.builder()
                .setAccount(Account(email, "com.google"))
                .setScopes(scopes)
                .build(),
        ).await()
    }

    private fun AuthorizationResult.toOutcome(): Outcome {
        val intent = pendingIntent
        return when {
            hasResolution() && intent != null -> Outcome.NeedsConsent(intent)
            accessToken != null -> {
                // Google's consent screen lets the user untick individual permissions. An empty
                // list means Play services didn't say, so don't lock the user out on a guess.
                val missing = GoogleScopes.ALL.filter { it !in grantedScopes }
                if (grantedScopes.isEmpty() || missing.isEmpty()) {
                    Outcome.Authorized(accessToken!!)
                } else {
                    Outcome.MissingScopes(accessToken!!, missing)
                }
            }
            else -> error("Google returned neither a token nor a consent screen")
        }
    }
}
