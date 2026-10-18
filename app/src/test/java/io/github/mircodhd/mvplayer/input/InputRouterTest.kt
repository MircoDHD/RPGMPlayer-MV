// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InputRouterTest {
    private val a = Controls.key(Controls.KEY_BUTTON_A)
    private val b = Controls.key(Controls.KEY_BUTTON_B)
    private val start = Controls.key(Controls.KEY_BUTTON_START)
    private val select = Controls.key(Controls.KEY_BUTTON_SELECT)
    private val hatUp = Controls.axis("HAT_Y", false)

    private val bits = mutableListOf<Int>()
    private val appActions = mutableListOf<InputAction>()
    private lateinit var router: InputRouter

    @Before
    fun setUp() {
        router = InputRouter(ControllerMapping.DEFAULT, { bits += it }, { appActions += it })
    }

    @Test
    fun defaultMappingMatchesPreviousBehaviour() {
        assertEquals(setOf(InputAction.OK), router.resolve(setOf(a)))
        assertEquals(setOf(InputAction.CANCEL), router.resolve(setOf(b)))
        assertEquals(setOf(InputAction.MENU), router.resolve(setOf(start)))
        assertEquals(setOf(InputAction.UP), router.resolve(setOf(hatUp)))
    }

    @Test
    fun bitsUseTheStandardGamepadLayout() {
        router.setHardware(setOf(a, hatUp))
        assertEquals(listOf((1 shl 0) or (1 shl 12)), bits)
    }

    @Test
    fun chordSuppressesItsParts() {
        assertEquals(setOf(InputAction.QUICK_MENU), router.resolve(setOf(select, start)))
    }

    @Test
    fun appActionFiresOnceOnPress() {
        router.setHardware(setOf(select))
        router.setHardware(setOf(select, start))
        router.setHardware(setOf(select, start))
        router.setHardware(emptySet())
        router.setHardware(setOf(select, start))
        assertEquals(listOf(InputAction.QUICK_MENU, InputAction.QUICK_MENU), appActions)
    }

    @Test
    fun overlayAndHardwareCombine() {
        router.setHardware(setOf(a))
        router.setOverlay(setOf(InputAction.LEFT))
        assertEquals((1 shl 0) or (1 shl 14), bits.last())
        router.reset()
        assertEquals(0, bits.last())
    }

    @Test
    fun remappingSwapsButtons() {
        val swapped = ControllerMapping.DEFAULT
            .withRemoved(InputAction.OK, listOf(a)).withAdded(InputAction.OK, listOf(b))
            .withRemoved(InputAction.CANCEL, listOf(b)).withAdded(InputAction.CANCEL, listOf(a))
        router.mapping = swapped
        assertEquals(setOf(InputAction.CANCEL), router.resolve(setOf(a)))
        assertEquals(setOf(InputAction.OK), router.resolve(setOf(b)))
    }

    @Test
    fun noOutputWithoutChange() {
        router.setHardware(setOf(a))
        router.setHardware(setOf(a))
        assertEquals(1, bits.size)
    }

    @Test
    fun unknownControlsAreIgnored() {
        assertTrue(router.resolve(setOf("k999")).isEmpty())
    }
}
