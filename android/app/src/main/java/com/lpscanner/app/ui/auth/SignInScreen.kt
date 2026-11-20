package com.lpscanner.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.lpscanner.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lpscanner.app.ui.theme.AppColors
import com.lpscanner.app.ui.theme.AppIcons

/**
 * The app's front door. Nothing else is reachable until the user signs in and grants both
 * permissions; the error from a cancelled or partial sign-in shows under the button.
 */
@Composable
fun SignInScreen(busy: Boolean, message: String?, onSignIn: () -> Unit) {
    // At least screen-tall, so the button sits at the bottom; scrolls on small screens / large fonts.
    BoxWithConstraints(Modifier.fillMaxSize().background(AppColors.Surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .heightIn(min = maxHeight)
            .padding(start = 28.dp, end = 28.dp, top = 72.dp, bottom = 32.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            AppLogo()
            Text(stringResource(R.string.app_name), fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 16.dp)) {
                Feature(AppIcons.Sheet, stringResource(R.string.signin_feature_sheet))
                Feature(AppIcons.Camera, stringResource(R.string.signin_feature_scan))
                Feature(AppIcons.Clock, stringResource(R.string.signin_feature_seen))
            }
        }
        Spacer(Modifier.height(48.dp))
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onSignIn,
                enabled = !busy,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = AppColors.OnPrimary, strokeWidth = 2.dp)
                } else {
                    Box(
                        Modifier.size(28.dp).background(AppColors.OnPrimary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("G", color = AppColors.Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.signin_button), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (message != null) {
                Text(message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Text(
                stringResource(R.string.signin_permissions),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = AppColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
    }
}

/** Shown while the app checks for an existing sign-in or opens the saved sheet. */
@Composable
fun SplashScreen(label: String? = null) {
    Column(
        Modifier.fillMaxSize().background(AppColors.Surface).windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogo()
        CircularProgressIndicator(Modifier.padding(top = 32.dp).size(28.dp), strokeWidth = 3.dp)
        if (label != null) Text(label, color = AppColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

/** The launcher icon (plate in scanner brackets), same blue in light and dark themes. */
@Composable
private fun AppLogo() {
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1F5FD1)),
        contentAlignment = Alignment.Center,
    ) {
        // The icon's 108-unit canvas shows its middle 72 units on a launcher; crop it the same way.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(96.dp),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
        Text(text, fontSize = 15.sp)
    }
}
