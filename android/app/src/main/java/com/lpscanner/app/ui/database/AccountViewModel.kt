package com.lpscanner.app.ui.database

import com.lpscanner.app.R
import android.app.Activity
import android.app.Application
import com.lpscanner.app.ui.inAppLanguage
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lpscanner.app.PlateScannerApp
import com.lpscanner.app.google.ConsentRequiredException
import com.lpscanner.app.google.GoogleAuth
import com.lpscanner.app.google.GoogleUser
import com.lpscanner.app.google.PermissionsMissingException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AccountState {
    data object Checking : AccountState
    data class SignedOut(val message: String? = null) : AccountState
    data class SignedIn(val user: GoogleUser) : AccountState
}

data class AccountUiState(
    val account: AccountState = AccountState.Checking,
    val busy: Boolean = false,
    /** Set when Google's consent screen must be shown; the UI launches it, then clears it. */
    val consentRequest: PendingIntent? = null,
)

class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PlateScannerApp).container
    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        // Silent check: succeeds without UI if the user consented before.
        viewModelScope.launch {
            val account = try {
                when (val outcome = container.googleAuth.authorize()) {
                    is GoogleAuth.Outcome.Authorized -> {
                        container.googleSession.accept(outcome.accessToken)
                        AccountState.SignedIn(container.drive.currentUser())
                    }
                    is GoogleAuth.Outcome.NeedsConsent -> AccountState.SignedOut()
                    is GoogleAuth.Outcome.MissingScopes -> AccountState.SignedOut(text(R.string.signin_missing_permissions))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AccountState.SignedOut(text(R.string.signin_unreachable, e.message.orEmpty()))
            }
            _state.update { it.copy(account = account) }
        }
    }

    fun signIn() = runBusy { handle(container.googleAuth.authorize()) }

    /** Another screen hit "consent required" mid-session; show Google's screen. */
    fun requestConsent(pendingIntent: PendingIntent) = _state.update { it.copy(consentRequest = pendingIntent) }

    fun onConsentLaunched() = _state.update { it.copy(consentRequest = null) }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            _state.update { it.copy(account = AccountState.SignedOut(failedConsentMessage(data))) }
            return
        }
        runBusy { handle(container.googleAuth.outcomeFromConsentIntent(data)) }
    }

    /**
     * A screen found that Google no longer grants everything the app needs (the user removed a
     * permission in their Google account). Back to the sign-in screen, which asks again.
     */
    fun onPermissionsMissing() {
        _state.update { it.copy(account = AccountState.SignedOut(text(R.string.signin_missing_permissions))) }
        viewModelScope.launch { runCatching { container.googleSession.forget() } }
    }

    private suspend fun handle(outcome: GoogleAuth.Outcome) {
        when (outcome) {
            is GoogleAuth.Outcome.Authorized -> loadUser(outcome.accessToken)
            is GoogleAuth.Outcome.NeedsConsent -> _state.update { it.copy(consentRequest = outcome.pendingIntent) }
            is GoogleAuth.Outcome.MissingScopes -> {
                container.googleAuth.clearToken(outcome.token)
                _state.update { it.copy(account = AccountState.SignedOut(text(R.string.signin_missing_permissions))) }
            }
        }
    }

    fun signOut() = runBusy {
        val signedIn = _state.value.account as? AccountState.SignedIn ?: return@runBusy
        container.googleSession.signOut(signedIn.user.emailAddress)
        _state.update { it.copy(account = AccountState.SignedOut()) }
    }

    /** Google's screen closes with "not OK" both when the user backs out and when it fails; tell them apart. */
    private fun failedConsentMessage(data: Intent?): String {
        val error = runCatching { container.googleAuth.outcomeFromConsentIntent(data) }.exceptionOrNull() as? ApiException
        return when (error?.statusCode) {
            null, CommonStatusCodes.CANCELED -> text(R.string.signin_cancelled)
            CommonStatusCodes.NETWORK_ERROR -> text(R.string.signin_network)
            else -> text(R.string.signin_failed, error.statusCode, error.message.orEmpty())
        }
    }

    private suspend fun loadUser(token: String) {
        container.googleSession.accept(token)
        val user = container.drive.currentUser()
        _state.update { it.copy(account = AccountState.SignedIn(user)) }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ConsentRequiredException) {
                _state.update { it.copy(consentRequest = e.pendingIntent) }
            } catch (e: PermissionsMissingException) {
                _state.update { it.copy(account = AccountState.SignedOut(text(R.string.signin_missing_permissions))) }
            } catch (e: Exception) {
                _state.update { it.copy(account = AccountState.SignedOut(text(R.string.error_generic, e.message.orEmpty()))) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().inAppLanguage().getString(id, *args)
}
