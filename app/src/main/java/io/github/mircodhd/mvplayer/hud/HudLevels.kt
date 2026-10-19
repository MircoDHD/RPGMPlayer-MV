// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.hud

import android.os.PowerManager

/**
 * How a HUD value is doing. Only the core frame metrics are ever GOOD (green); every
 * other value stays neutral until it gets close to a limit (WARN, yellow) or past it
 * (BAD, red), so the HUD does not turn into a colourful wall.
 * Frame thresholds are relative to the chosen frame rate: at 30 fps, 30 fps is good.
 */
enum class HudLevel { GOOD, WARN, BAD }

object HudLevels {
    /** Frames slower than this many target frames turn yellow; red from two. */
    const val WARN_FRAMES = 1.25
    const val BAD_FRAMES = 2.0

    /** Target frame time: the frame rate cap, or 60 fps when uncapped. */
    fun targetMs(frameIntervalMs: Double?): Double =
        frameIntervalMs?.takeIf { it > 0 } ?: (1000.0 / 60)

    fun frameTime(ms: Double, targetMs: Double): HudLevel = when {
        ms <= targetMs * WARN_FRAMES -> HudLevel.GOOD
        ms <= targetMs * BAD_FRAMES -> HudLevel.WARN
        else -> HudLevel.BAD
    }

    /** Frame rate against the target: green from 80 %, red below 50 %. */
    fun fps(fps: Double, targetMs: Double): HudLevel = frameTime(if (fps > 0) 1000.0 / fps else Double.MAX_VALUE, targetMs)

    /** MV's logic runs at 60 steps per second; fewer means the game itself slows down. */
    fun logic(stepsPerSecond: Double): HudLevel? = when {
        stepsPerSecond < 45 -> HudLevel.BAD
        stepsPerSecond < 55 -> HudLevel.WARN
        else -> null
    }

    /** JavaScript time per frame against the frame budget. */
    fun jsTime(ms: Double, targetMs: Double): HudLevel? = when {
        ms >= targetMs -> HudLevel.BAD
        ms >= targetMs * 0.6 -> HudLevel.WARN
        else -> null
    }

    fun memory(usedMb: Int, limitMb: Int?): HudLevel? {
        if (limitMb == null || limitMb <= 0) return null
        val share = usedMb.toDouble() / limitMb
        return when {
            share >= 0.9 -> HudLevel.BAD
            share >= 0.7 -> HudLevel.WARN
            else -> null
        }
    }

    fun battery(percent: Int?, charging: Boolean?, tempC: Float?): HudLevel? = worst(
        when {
            percent == null || charging == true -> null
            percent <= 15 -> HudLevel.BAD
            percent <= 30 -> HudLevel.WARN
            else -> null
        },
        when {
            tempC == null -> null
            tempC >= 45f -> HudLevel.BAD
            tempC >= 40f -> HudLevel.WARN
            else -> null
        },
    )

    /** Android's thermal status and headroom (1.0 = severe throttling expected). */
    fun thermal(status: Int?, headroom: Float?): HudLevel? = worst(
        when {
            status == null -> null
            status >= PowerManager.THERMAL_STATUS_SEVERE -> HudLevel.BAD
            status >= PowerManager.THERMAL_STATUS_LIGHT -> HudLevel.WARN
            else -> null
        },
        when {
            headroom == null -> null
            headroom >= 0.95f -> HudLevel.BAD
            headroom >= 0.75f -> HudLevel.WARN
            else -> null
        },
    )

    fun worst(vararg levels: HudLevel?): HudLevel? = levels.filterNotNull().maxByOrNull { it.ordinal }
}
