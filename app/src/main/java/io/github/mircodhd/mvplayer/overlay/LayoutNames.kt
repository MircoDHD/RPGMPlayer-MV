// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.overlay

import android.content.Context
import io.github.mircodhd.mvplayer.R

/** Display names of layouts: string resources for presets, stored names for custom ones. */
object LayoutNames {
    private val presetNames = mapOf(
        "preset_handheld" to R.string.preset_handheld,
        "preset_console16" to R.string.preset_console16,
        "preset_arcade6" to R.string.preset_arcade6,
        "preset_dualanalog" to R.string.preset_dualanalog,
        "preset_minimal" to R.string.preset_minimal,
    )

    fun name(context: Context, layout: GamepadLayout): String =
        layout.name ?: layout.nameKey?.let(presetNames::get)?.let(context::getString) ?: layout.id

    /** "Custom (based on …)" for a copy of a preset; a custom layout keeps its name. */
    fun customName(context: Context, base: GamepadLayout): String =
        if (base.builtIn) context.getString(R.string.layout_custom_name, name(context, base)) else name(context, base)
}
