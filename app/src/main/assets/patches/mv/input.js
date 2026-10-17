// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * MV Player: controller input from the host. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * The host captures controllers natively and calls MvPlayer.setPad(bitmask) with the
 * W3C standard-gamepad button layout. navigator.getGamepads() is not used at all, so
 * behaviour does not depend on the WebView's Gamepad API support.
 */
(function () {
    'use strict';

    var BUTTON_COUNT = 17;
    var current = 0;
    // Buttons pressed since the last poll, so a tap shorter than one frame is not lost.
    var latched = 0;

    MvPlayer.setPad = function (buttons) {
        current = buttons;
        latched |= buttons;
        if (MvPlayer.unlockAudio) MvPlayer.unlockAudio();
    };

    // Start opens the menu, as most players expect.
    if (Input.gamepadMapper[9] === undefined) {
        Input.gamepadMapper[9] = 'menu';
    }

    // Same edge detection as vanilla Input._updateGamepadState, fed from the host's state.
    Input._pollGamepads = function () {
        var buttons = current | latched;
        latched = 0;
        var lastState = this._gamepadStates[0] || [];
        var newState = [];
        for (var i = 0; i < BUTTON_COUNT; i++) {
            newState[i] = (buttons & (1 << i)) !== 0;
        }
        for (var j = 0; j < BUTTON_COUNT; j++) {
            if (newState[j] !== lastState[j]) {
                var buttonName = this.gamepadMapper[j];
                if (buttonName) {
                    this._currentState[buttonName] = newState[j];
                }
            }
        }
        this._gamepadStates[0] = newState;
    };
})();
