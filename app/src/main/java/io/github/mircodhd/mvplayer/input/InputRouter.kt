// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer.input

/**
 * Combines hardware controls (through the [mapping]) and on-screen controls (already
 * actions) into the bitmask sent to the game, and fires app actions on press.
 *
 * Chords win over their parts: while Select + Start is held as the quick-menu chord,
 * Start does not also open the game menu.
 */
class InputRouter(
    mapping: ControllerMapping,
    private val onBits: (Int) -> Unit,
    private val onAppAction: (InputAction) -> Unit,
) {
    var mapping: ControllerMapping = mapping
        set(value) {
            field = value
            update()
        }

    private var hardware: Set<String> = emptySet()
    private var overlay: Set<InputAction> = emptySet()
    private var lastBits = 0
    private var activeAppActions: Set<InputAction> = emptySet()

    fun setHardware(controls: Set<String>) {
        hardware = controls
        update()
    }

    fun setOverlay(actions: Set<InputAction>) {
        overlay = actions
        update()
    }

    fun reset() {
        hardware = emptySet()
        overlay = emptySet()
        update()
    }

    /** Actions triggered by the held [controls]; chords first, longest first. */
    fun resolve(controls: Set<String>): Set<InputAction> {
        if (controls.isEmpty()) return emptySet()
        val active = HashSet<InputAction>()
        val consumed = HashSet<String>()
        mapping.bindings
            .flatMap { (action, list) -> list.filter { it.size > 1 }.map { action to it } }
            .sortedByDescending { it.second.size }
            .forEach { (action, chord) ->
                if (controls.containsAll(chord)) {
                    active += action
                    consumed += chord
                }
            }
        for ((action, list) in mapping.bindings) {
            if (list.any { it.size == 1 && it[0] in controls && it[0] !in consumed }) active += action
        }
        return active
    }

    private fun update() {
        val actions = resolve(hardware) + overlay
        val app = actions.filterTo(HashSet()) { it.isAppAction }
        (app - activeAppActions).forEach(onAppAction)
        activeAppActions = app
        val bits = InputAction.bitsOf(actions)
        if (bits != lastBits) {
            lastBits = bits
            onBits(bits)
        }
    }
}
