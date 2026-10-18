// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.overlay.LayoutEditor
import io.github.mircodhd.mvplayer.overlay.LayoutNames
import io.github.mircodhd.mvplayer.overlay.LayoutRepository
import io.github.mircodhd.mvplayer.settings.SettingsStore
import io.github.mircodhd.mvplayer.ui.theme.MvPlayerTheme

/**
 * Landscape screen for editing an on-screen layout outside a game (neutral background).
 * Saves a custom copy and selects it globally or for one game.
 */
class LayoutEditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LAYOUT_ID = "layout_id"
        const val EXTRA_GAME_ID = "game_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        val repository = LayoutRepository(this)
        val store = SettingsStore.get(this)
        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        val layout = repository.find(intent.getStringExtra(EXTRA_LAYOUT_ID).orEmpty())
        val preset = repository.presets().firstOrNull { it.id == (layout.basedOn ?: layout.id) } ?: layout
        val overlay = store.effective(gameId).overlay
        // Edit what the player sees: mirrored if left-handed mode is on (saved as shown).
        val shown = if (overlay.mirror) layout.mirrored() else layout
        val original = if (overlay.mirror) preset.mirrored() else preset

        setContent {
            MvPlayerTheme {
                LayoutEditor(
                    initial = shown,
                    original = original,
                    background = null,
                    scale = overlay.scale,
                    canSaveForGame = gameId != null,
                    onSave = { edited, forGame ->
                        val saved = repository.saveCustom(edited, LayoutNames.customName(this, layout))
                        if (forGame && gameId != null) {
                            store.setGame(gameId, store.game(gameId).copy(overlayLayoutId = saved.id, overlayMirror = false))
                        } else {
                            val global = store.global()
                            store.setGlobal(global.copy(overlay = global.overlay.copy(layoutId = saved.id, mirror = false)))
                        }
                        DebugLog.i("Layouts", "Layout ${saved.id} selected ${if (forGame) "for game $gameId" else "globally"}")
                        finish()
                    },
                    onCancel = ::finish,
                )
            }
        }
    }
}
