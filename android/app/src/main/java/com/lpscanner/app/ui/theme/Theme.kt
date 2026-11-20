package com.lpscanner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Every color the screens use, in a light and a dark version. */
data class Palette(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    /** Links and the selected tab: readable on the screen background. */
    val primaryText: Color,
    val primaryTint: Color,
    val primaryTintLight: Color,
    val ink: Color,
    val muted: Color,
    val background: Color,
    val surface: Color,
    val field: Color,
    val border: Color,
    val divider: Color,
    val rowDivider: Color,
    val danger: Color,
    val dangerBg: Color,
    val dangerBorder: Color,
    val infoBg: Color,
    val infoBorder: Color,
    /** "In database" banner. */
    val success: Color,
    val successBg: Color,
    val successBorder: Color,
    val successText: Color,
    /** "Not in database" banner and notes. */
    val warning: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val warningText: Color,
)

/** Colors from the app design (claude.ai canvas). */
val LightPalette = Palette(
    isDark = false,
    primary = Color(0xFF1F5FD1),
    onPrimary = Color.White,
    primaryText = Color(0xFF174BA8), // primary darkened for 4.5:1 on white
    primaryTint = Color(0xFFDCE7FA),
    primaryTintLight = Color(0xFFE8EFFC),
    ink = Color(0xFF20314B),
    muted = Color(0xFF4A5A72),
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    field = Color(0xFFEEF1F5),
    border = Color(0xFFB9C2CF),
    divider = Color(0xFFDCE1E8),
    rowDivider = Color(0xFFE8ECF1),
    danger = Color(0xFFC40000),
    dangerBg = Color(0xFFFDE7E7),
    dangerBorder = Color(0xFFF2B8B8),
    infoBg = Color(0xFFEEF3FD),
    infoBorder = Color(0xFFC4D5F5),
    success = Color(0xFF0F6B4E),
    successBg = Color(0xFFE3F3EE),
    successBorder = Color(0xFF9ED3C0),
    successText = Color(0xFF0A5039),
    warning = Color(0xFF8A4B00),
    warningBg = Color(0xFFFFF4E0),
    warningBorder = Color(0xFFF0C77E),
    warningText = Color(0xFF6B3A00),
)

/**
 * Not in the design: the same blue/slate family on a dark slate background. Buttons use a light
 * blue with dark text (Material's dark-theme convention); text pairs meet 4.5:1 contrast.
 */
val DarkPalette = Palette(
    isDark = true,
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF0A2A5E),
    primaryText = Color(0xFFA8C7FA),
    primaryTint = Color(0xFF243A61),
    primaryTintLight = Color(0xFF1C2C48),
    ink = Color(0xFFE4E8EF),
    muted = Color(0xFFA3AEBF),
    background = Color(0xFF0F1318),
    surface = Color(0xFF181D24),
    field = Color(0xFF242B35),
    border = Color(0xFF4A5566),
    divider = Color(0xFF2C3440),
    rowDivider = Color(0xFF252C36),
    danger = Color(0xFFFF8A80),
    dangerBg = Color(0xFF3A1717),
    dangerBorder = Color(0xFF7A2E2E),
    infoBg = Color(0xFF17253C),
    infoBorder = Color(0xFF2E4670),
    success = Color(0xFF6CCBA5),
    successBg = Color(0xFF11302A),
    successBorder = Color(0xFF2C6B57),
    successText = Color(0xFF94DCC0),
    warning = Color(0xFFF0B85A),
    warningBg = Color(0xFF382810),
    warningBorder = Color(0xFF7A5520),
    warningText = Color(0xFFF5CF8E),
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * The current theme's colors, as `AppColors.Primary` etc. (read during composition). Plates
 * stay black on yellow in both themes, like the real thing.
 */
object AppColors {
    private val p: Palette @Composable @ReadOnlyComposable get() = LocalPalette.current

    val IsDark: Boolean @Composable @ReadOnlyComposable get() = p.isDark
    val Primary: Color @Composable @ReadOnlyComposable get() = p.primary
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = p.onPrimary
    val PrimaryText: Color @Composable @ReadOnlyComposable get() = p.primaryText
    val PrimaryTint: Color @Composable @ReadOnlyComposable get() = p.primaryTint
    val PrimaryTintLight: Color @Composable @ReadOnlyComposable get() = p.primaryTintLight
    val Ink: Color @Composable @ReadOnlyComposable get() = p.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = p.muted
    val Background: Color @Composable @ReadOnlyComposable get() = p.background
    val Surface: Color @Composable @ReadOnlyComposable get() = p.surface
    val Field: Color @Composable @ReadOnlyComposable get() = p.field
    val Border: Color @Composable @ReadOnlyComposable get() = p.border
    val Divider: Color @Composable @ReadOnlyComposable get() = p.divider
    val RowDivider: Color @Composable @ReadOnlyComposable get() = p.rowDivider
    val Danger: Color @Composable @ReadOnlyComposable get() = p.danger
    val DangerBg: Color @Composable @ReadOnlyComposable get() = p.dangerBg
    val DangerBorder: Color @Composable @ReadOnlyComposable get() = p.dangerBorder
    val InfoBg: Color @Composable @ReadOnlyComposable get() = p.infoBg
    val InfoBorder: Color @Composable @ReadOnlyComposable get() = p.infoBorder
    val Success: Color @Composable @ReadOnlyComposable get() = p.success
    val SuccessBg: Color @Composable @ReadOnlyComposable get() = p.successBg
    val SuccessBorder: Color @Composable @ReadOnlyComposable get() = p.successBorder
    val SuccessText: Color @Composable @ReadOnlyComposable get() = p.successText
    val Warning: Color @Composable @ReadOnlyComposable get() = p.warning
    val WarningBg: Color @Composable @ReadOnlyComposable get() = p.warningBg
    val WarningBorder: Color @Composable @ReadOnlyComposable get() = p.warningBorder
    val WarningText: Color @Composable @ReadOnlyComposable get() = p.warningText

    val PlateYellow = Color(0xFFF9D43A)
    val PlateInk = Color(0xFF111111)
}

private fun Palette.scheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryTint,
        onPrimaryContainer = primaryText,
        secondary = muted,
        onSecondary = surface,
        secondaryContainer = warningBg,
        onSecondaryContainer = warningText,
        tertiary = success,
        onTertiary = surface,
        tertiaryContainer = successBg,
        onTertiaryContainer = successText,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = field,
        onSurfaceVariant = muted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = if (isDark) Color(0xFF1F252E) else Color(0xFFF2F4F7),
        surfaceContainerHighest = field,
        inverseSurface = ink,
        inverseOnSurface = surface,
        outline = border,
        outlineVariant = divider,
        error = danger,
        onError = if (isDark) Color(0xFF5C0000) else Color.White,
        errorContainer = dangerBg,
        onErrorContainer = danger,
    )
}

private val LightScheme = LightPalette.scheme()
private val DarkScheme = DarkPalette.scheme()

/** Follows the phone's light/dark setting. */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
            // Text outside a Material Surface (screens that paint their own background) would
            // otherwise default to black, unreadable in the dark theme.
            CompositionLocalProvider(LocalContentColor provides palette.ink, content = content)
        }
    }
}

/** Bottom navigation: tinted pill behind the selected tab, as in the design. */
@Composable
fun navigationItemColors(): NavigationBarItemColors = NavigationBarItemDefaults.colors(
    selectedIconColor = AppColors.PrimaryText,
    selectedTextColor = AppColors.PrimaryText,
    indicatorColor = AppColors.PrimaryTint,
    unselectedIconColor = AppColors.Muted,
    unselectedTextColor = AppColors.Muted,
)

/**
 * Status/navigation bar icons for a full-screen Dialog to match the theme: dialogs get their own
 * window, which doesn't inherit the activity's setting. Call inside the Dialog's content.
 */
@Composable
fun DialogSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val light = !AppColors.IsDark
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
