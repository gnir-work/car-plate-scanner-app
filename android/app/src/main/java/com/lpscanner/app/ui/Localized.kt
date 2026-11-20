package com.lpscanner.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import com.lpscanner.app.R
import com.lpscanner.app.database.CarVocabulary
import com.lpscanner.app.database.LastSeen
import com.lpscanner.app.database.OptionKind
import java.time.format.DateTimeFormatter
import java.util.Locale

// Turning stored values into text in the user's language. The sheet itself is always English.

@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** Hebrew UI (Android reports Hebrew as "iw" on older versions and "he" on newer ones). */
@Composable
fun isHebrew(): Boolean = appLocale().language in setOf("he", "iw")

/** How a stored (English) company or color is shown: in Hebrew when the UI is Hebrew and we know the name. */
@Composable
fun displayName(kind: OptionKind, stored: String): String =
    if (isHebrew()) CarVocabulary.toHebrew(kind, stored) else stored

/** Display names for every stored value, for matching what the user types in a search. */
@Composable
fun displayNamer(): (OptionKind, String) -> String {
    val hebrew = isHebrew()
    return remember(hebrew) { { kind, stored -> if (hebrew) CarVocabulary.toHebrew(kind, stored) else stored } }
}

/** "5 min ago" / "לפני 5 דקות"; [never] when empty. */
@Composable
fun agoText(lastSeen: String?): String = if (lastSeen.isNullOrBlank()) stringResource(R.string.never) else when (val ago = LastSeen.ago(lastSeen)) {
    LastSeen.Ago.JustNow -> stringResource(R.string.ago_just_now)
    is LastSeen.Ago.Minutes -> pluralStringResource(R.plurals.ago_minutes, ago.count, ago.count)
    is LastSeen.Ago.Hours -> pluralStringResource(R.plurals.ago_hours, ago.count, ago.count)
    LastSeen.Ago.Yesterday -> stringResource(R.string.ago_yesterday)
    is LastSeen.Ago.Days -> pluralStringResource(R.plurals.ago_days, ago.count, ago.count)
    is LastSeen.Ago.On -> ago.date.format(formatter(R.string.pattern_date_short))
    is LastSeen.Ago.Text -> ago.value
}

/** "Today 14:32" / "17 Nov, 08:15" / "היום 14:32"; [empty] (default "Never") when there's no value. */
@Composable
fun momentText(lastSeen: String?, empty: Int = R.string.never): String {
    if (lastSeen.isNullOrBlank()) return stringResource(empty)
    val time = DateTimeFormatter.ofPattern("HH:mm")
    return when (val moment = LastSeen.moment(lastSeen)) {
        is LastSeen.Moment.Today -> stringResource(R.string.moment_today, moment.time.format(time))
        is LastSeen.Moment.Yesterday -> stringResource(R.string.moment_yesterday, moment.time.format(time))
        is LastSeen.Moment.ThisYear -> moment.dateTime.format(formatter(R.string.pattern_day_time))
        is LastSeen.Moment.Older -> moment.date.format(formatter(R.string.pattern_date_year))
        is LastSeen.Moment.Text -> moment.value
    }
}

@Composable
private fun formatter(pattern: Int): DateTimeFormatter = DateTimeFormatter.ofPattern(stringResource(pattern), appLocale())

/**
 * Plates are always left-to-right ("12-345-67"), also inside a Hebrew screen; in a right-to-left
 * layout they still hug the right edge of their field, as in the Hebrew design.
 */
@Composable
fun plateTextStyle(base: TextStyle): TextStyle = base.merge(
    TextStyle(
        textDirection = TextDirection.Ltr,
        textAlign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
    ),
)
