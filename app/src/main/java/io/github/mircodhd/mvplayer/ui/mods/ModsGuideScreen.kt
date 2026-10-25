// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.ui.mods

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mircodhd.mvplayer.R
import io.github.mircodhd.mvplayer.ui.settings.Code
import io.github.mircodhd.mvplayer.ui.settings.GuideNavigation
import io.github.mircodhd.mvplayer.ui.settings.Paragraph
import io.github.mircodhd.mvplayer.ui.settings.StepHeader

/** Step-by-step guide to mods: where to put them, order, conflicts, mod.json, saves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModsGuideScreen(onBack: () -> Unit) {
    val pager = rememberPagerState { STEPS }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mods_guide_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.set_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    StepHeader(page + 1, stringResource(TITLES[page]))
                    when (page) {
                        0 -> {
                            Paragraph(stringResource(R.string.mods_guide_1_text))
                            Paragraph(stringResource(R.string.mods_guide_1_warning), warning = true)
                        }
                        1 -> {
                            Paragraph(stringResource(R.string.mods_guide_2_text))
                            Code(stringResource(R.string.mods_info_tree))
                            Paragraph(stringResource(R.string.mods_guide_2_note))
                        }
                        2 -> {
                            Paragraph(stringResource(R.string.mods_guide_3_text))
                            Paragraph(stringResource(R.string.mods_guide_3_restart))
                        }
                        3 -> {
                            Paragraph(stringResource(R.string.mods_guide_4_text))
                            Paragraph(stringResource(R.string.mods_guide_4_core), warning = true)
                        }
                        4 -> {
                            Paragraph(stringResource(R.string.mods_guide_5_text))
                            Code(MOD_JSON)
                            Paragraph(stringResource(R.string.mods_guide_5_scripts))
                        }
                        else -> {
                            Paragraph(stringResource(R.string.mods_guide_6_saves))
                            Paragraph(stringResource(R.string.mods_guide_6_remove))
                        }
                    }
                }
            }
            GuideNavigation(pager, STEPS, onBack)
        }
    }
}

private const val STEPS = 6

private val TITLES = listOf(
    R.string.mods_guide_1_title,
    R.string.mods_guide_2_title,
    R.string.mods_guide_3_title,
    R.string.mods_guide_4_title,
    R.string.mods_guide_5_title,
    R.string.mods_guide_6_title,
)

private val MOD_JSON = """
    {
      "name": "Better Icons",
      "version": "1.2",
      "author": "Someone",
      "description": "New item icons",
      "scripts": [
        { "file": "js/fix.js", "position": "afterPlugins" }
      ]
    }
""".trimIndent()
