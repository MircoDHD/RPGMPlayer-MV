// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.debug.DebugLog
import java.util.Locale

/** Loads the changelog in the device language (German or English). */
fun loadChangelog(context: Context): List<ChangelogEntry> {
    val language = if (Locale.getDefault().language == "de") "de" else "en"
    return runCatching {
        context.assets.open("changelog/$language.md").use { Changelog.parse(it.reader().readText()) }
    }.onFailure { DebugLog.w("Changelog", "Cannot read the $language changelog", it) }.getOrDefault(emptyList())
}

/** "What's new" box: the given versions, newest first, formatted from the Markdown. */
@Composable
fun ChangelogDialog(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (entries.size == 1) {
                    stringResource(R.string.changelog_title_version, entries.first().version)
                } else {
                    stringResource(R.string.changelog_title)
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    if (entries.size > 1) {
                        Text(entry.heading, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    } else {
                        // The version is in the title already; the date and any phase name remain.
                        entry.heading.substringAfter(' ', "").trim().trimStart('–', '-', ' ').takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    for (block in entry.blocks) ChangelogBlockView(block)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_ok)) } },
    )
}

@Composable
private fun ChangelogBlockView(block: ChangelogBlock) {
    when (block) {
        is ChangelogBlock.Section -> {
            Spacer(Modifier.height(12.dp))
            Text(block.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
        }
        is ChangelogBlock.Bullet -> Row(Modifier.padding(vertical = 3.dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(inline(block.text), style = MaterialTheme.typography.bodyMedium)
        }
        is ChangelogBlock.Paragraph -> Text(inline(block.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
    }
}

/** **bold**, *italic* and `code` spans. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) && text.indexOf("**", i + 2) > 0 -> {
                val end = text.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text, i + 2, end) }
                i = end + 2
            }
            text[i] == '*' && text.indexOf('*', i + 1) > i + 1 -> {
                val end = text.indexOf('*', i + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text, i + 1, end) }
                i = end + 1
            }
            text[i] == '`' && text.indexOf('`', i + 1) > 0 -> {
                val end = text.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text, i + 1, end) }
                i = end + 1
            }
            else -> append(text[i++])
        }
    }
}
