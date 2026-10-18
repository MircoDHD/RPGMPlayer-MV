// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * MV Player: frame rate cap. Runs after rpg_*.js, before plugins.
 * Patch version: 2
 *
 * MV runs its game logic at a fixed 60 steps per second, but renders once per display
 * refresh and counts rendered frames as play time (Graphics.frameCount). On a 120 Hz
 * display the play time therefore runs twice as fast and every image is drawn twice.
 * The host asks Android for a matching display rate; this gate additionally skips
 * refreshes that come too early, for displays that stay faster.
 * MvPlayer.setFrameRateCap(n) changes the cap at runtime; 0 renders at the display rate.
 */
(function () {
    'use strict';

    var interval = 0;
    var tolerance = 0;
    var nextFrame = 0;

    MvPlayer.setFrameRateCap = function (cap) {
        cap = Math.max(0, Number(cap) || 0);
        interval = cap > 0 ? 1000 / cap : 0;
        // rAF timestamps jitter slightly; accept a frame a quarter interval early.
        tolerance = interval / 4;
        nextFrame = 0;
        MvPlayer.log('i', 'Frames', cap > 0 ? 'Frame rate capped at ' + cap + ' fps' : 'Frame rate not capped');
    };

    // Replaces vanilla requestUpdate (which requests the next animation frame directly).
    // lifecycle.js wraps this, so pausing still works.
    SceneManager.requestUpdate = function () {
        if (this._stopped) return;
        var self = this;
        requestAnimationFrame(function gate(now) {
            if (interval > 0) {
                if (now < nextFrame - tolerance) {
                    requestAnimationFrame(gate);
                    return;
                }
                // Keep a steady cadence; resynchronise after a stall instead of catching up.
                nextFrame = now - nextFrame > interval ? now + interval : nextFrame + interval;
            }
            self.update();
        });
    };

    MvPlayer.setFrameRateCap(MvPlayer.config.frameRateCap || 0);
})();
