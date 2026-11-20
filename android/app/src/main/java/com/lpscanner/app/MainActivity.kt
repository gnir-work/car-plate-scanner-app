package com.lpscanner.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lpscanner.app.database.PlateSheetSetup.Inspection
import com.lpscanner.app.google.ConsentRequiredException
import com.lpscanner.app.ui.ScannerScreen
import com.lpscanner.app.ui.ScannerViewModel
import com.lpscanner.app.ui.auth.SignInScreen
import com.lpscanner.app.ui.auth.SplashScreen
import com.lpscanner.app.ui.database.AccountState
import com.lpscanner.app.ui.database.AccountViewModel
import com.lpscanner.app.ui.database.DatabaseScreen
import com.lpscanner.app.ui.database.PlatesViewModel
import com.lpscanner.app.ui.database.SheetDetails
import com.lpscanner.app.ui.database.SheetMode
import com.lpscanner.app.ui.database.SheetPickerScreen
import com.lpscanner.app.ui.database.SheetViewModel
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons
import com.lpscanner.app.ui.theme.AppTheme
import com.lpscanner.app.ui.theme.navigationItemColors

/** AppCompatActivity (not ComponentActivity) so the in-app language applies on Android 12 and older. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars; icons follow the phone's light/dark setting, like the app's theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            AppTheme { AppRoot() }
        }
    }
}

/**
 * The gate: sign in (with every permission granted) → choose a sheet → the app's tabs.
 * Each step blocks the next; losing a permission later sends the user back to sign-in.
 */
@Composable
private fun AppRoot(
    account: AccountViewModel = viewModel(),
    sheet: SheetViewModel = viewModel(),
) {
    GoogleAccountHost()
    val accountState by account.state.collectAsState()
    val sheetState by sheet.state.collectAsState()

    when (val signIn = accountState.account) {
        AccountState.Checking -> SplashScreen()
        is AccountState.SignedOut -> SignInScreen(busy = accountState.busy, message = signIn.message, onSignIn = account::signIn)
        is AccountState.SignedIn -> when (val mode = sheetState.mode) {
            SheetMode.Loading -> SplashScreen(stringResource(R.string.splash_opening))
            is SheetMode.Failed -> SheetFailedScreen(mode.message, onRetry = sheet::retry, onSignOut = account::signOut)
            is SheetMode.Picking -> SheetPickerScreen(
                user = signIn.user,
                mode = mode,
                busy = sheetState.busy || accountState.busy,
                onCreate = sheet::create,
                onPick = sheet::pick,
                onOpenLink = sheet::openLink,
                onRefresh = sheet::refreshList,
                onBack = if (mode.current != null) sheet::cancelSwitch else null,
                onSwitchAccount = account::signOut,
            )
            is SheetMode.Selected -> MainTabs(mode.details)
        }
    }

    sheetState.pendingSetup?.let { (_, needs) ->
        SetupDialog(needs, onConfirm = sheet::confirmSetup, onDismiss = sheet::cancelSetup)
    }
    sheetState.message?.let { message ->
        AlertDialog(
            onDismissRequest = sheet::dismissMessage,
            confirmButton = { TextButton(onClick = sheet::dismissMessage) { Text(stringResource(R.string.ok)) } },
            text = { Text(message) },
        )
    }
}

private enum class Tab(val label: Int, val icon: ImageVector) {
    Database(R.string.tab_database, AppIcons.List),
    Scan(R.string.tab_scanner, AppIcons.Scan),
}

@Composable
private fun MainTabs(details: SheetDetails) {
    var tab by rememberSaveable { mutableStateOf(Tab.Scan) }
    Scaffold(
        containerColor = AppColors.Surface,
        bottomBar = {
            NavigationBar(containerColor = AppColors.Surface) {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(it.icon, contentDescription = null) },
                        label = { Text(stringResource(it.label), fontWeight = FontWeight.Medium) },
                        colors = navigationItemColors(),
                    )
                }
            }
        },
    ) { padding ->
        // Each tab has its own top bar; hand it the space above the navigation bar.
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.Scan -> ScannerScreen()
                Tab.Database -> DatabaseScreen(details)
            }
        }
    }
}

/** The saved sheet couldn't be opened at startup (offline, Google error). */
@Composable
private fun SheetFailedScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(AppColors.Surface).windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Alert, contentDescription = null, tint = AppColors.Danger)
        Text(stringResource(R.string.sheet_failed_title), fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(message, color = AppColors.Muted, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_again)) }
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_out)) }
    }
}

@Composable
private fun SetupDialog(needs: Inspection.NeedsSetup, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val changes = buildList {
        if (needs.addPlatesTab) add(stringResource(R.string.setup_add_plates_tab))
        else if (needs.writePlatesHeader) add(stringResource(R.string.setup_add_header))
        if (needs.addOptionsTab) add(stringResource(R.string.setup_add_options_tab))
    }
    val and = stringResource(R.string.setup_and)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_title, needs.name)) },
        text = { Text(stringResource(R.string.setup_body, changes.joinToString(and))) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.setup_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * App-wide Google wiring: silent sign-in and restoring the selected sheet at startup, showing
 * Google's consent screen whenever any screen needs it, and returning to sign-in when a
 * permission goes missing.
 */
@Composable
private fun GoogleAccountHost(
    account: AccountViewModel = viewModel(),
    sheet: SheetViewModel = viewModel(),
    plates: PlatesViewModel = viewModel(),
    scanner: ScannerViewModel = viewModel(),
) {
    val accountState by account.state.collectAsState()
    val email = (accountState.account as? AccountState.SignedIn)?.user?.emailAddress

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        account.onConsentResult(it.resultCode, it.data)
    }
    LaunchedEffect(accountState.consentRequest) {
        accountState.consentRequest?.let {
            consentLauncher.launch(IntentSenderRequest.Builder(it).build())
            account.onConsentLaunched()
        }
    }
    LaunchedEffect(Unit) {
        val ask = { e: ConsentRequiredException -> account.requestConsent(e.pendingIntent) }
        sheet.onConsentRequired = ask
        plates.onConsentRequired = ask
        scanner.onConsentRequired = ask
        sheet.onPermissionsMissing = account::onPermissionsMissing
        plates.onPermissionsMissing = account::onPermissionsMissing
    }
    LaunchedEffect(email) { sheet.onAccount(email) }
}
