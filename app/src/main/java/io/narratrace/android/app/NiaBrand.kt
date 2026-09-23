package io.narratrace.android.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/** Native counterpart to the web signature; retains normal accessible text. */
@Composable
internal fun niaStyledText(value: String) = buildAnnotatedString {
    append(value)
    Regex("\\bNia(?:[’']s)?\\b").findAll(value).forEach { match ->
        addStyle(SpanStyle(fontFamily = FontFamily.Cursive, fontWeight = FontWeight.SemiBold,
            fontSize = 1.4.em, color = MaterialTheme.colorScheme.primary),
            match.range.first, match.range.last + 1)
    }
}
