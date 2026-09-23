package io.narratrace.android.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.narratrace.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

private val NiaSignature = FontFamily(Font(R.font.nia_signature))

/** Native counterpart to the web signature; retains normal accessible text. */
@Composable
internal fun niaStyledText(value: String) = buildAnnotatedString {
    append(value)
    Regex("\\bNia(?:[’']s)?\\b").findAll(value).forEach { match ->
        addStyle(SpanStyle(fontFamily = NiaSignature, fontWeight = FontWeight.Normal,
            fontSize = 1.4.em, color = MaterialTheme.colorScheme.primary),
            match.range.first, match.range.last + 1)
    }
}
