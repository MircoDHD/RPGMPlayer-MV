// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.saves

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.saves.GameSaves
import io.github.mircodhd.mvplayer.saves.SaveSlot
import io.github.mircodhd.mvplayer.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The game's own save slots, as its load menu shows them. Read only for now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSavesScreen(title: String, saves: GameSaves, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var slots by remember { mutableStateOf<List<SaveSlot>?>(null) }
    LaunchedEffect(saves) { slots = withContext(Dispatchers.IO) { saves.slots() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.game_saves_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                slots == null -> Text(stringResource(R.string.game_saves_loading), modifier = Modifier.padding(16.dp))
                slots!!.isEmpty() -> Text(stringResource(R.string.game_saves_empty), modifier = Modifier.padding(16.dp))
            }
            for (slot in slots.orEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.game_saves_slot, slot.number), style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                slot.info?.playtime?.takeIf { it.isNotEmpty() }?.let { resources.getString(R.string.app_saves_playtime, it) },
                                slot.info?.timestamp?.takeIf { it > 0 }?.let {
                                    DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
                                },
                                Format.size(context, slot.sizeBytes),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
