// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: frame rate cap and play time. Runs after rpg_*.js, before plugins.
 * Patch version: 4
 *
 * MV runs its game logic at a fixed 60 steps per second, but renders once per display
 * refresh. The host asks Android for a matching display rate; this gate additionally
 * skips refreshes that come too early, for displays that stay faster. The cap can be
 * 30 (battery saver), 60 or 0 (display rate). MvPlayer.setFrameRateCap(n) changes it at
 * runtime; MvPlayer.frameIntervalMs is the target gap between frames (0 = uncapped).
 *
 * Play time: MV counts rendered frames as play time (Graphics.frameCount / 60, also
 * stored in saves), so it ran twice as fast at 120 Hz and would run at half speed at
 * 30 fps. Each render now adds the number of logic steps since the previous render
 * instead of 1: 1 at 60 fps (as before), 2 at 30 fps, 1 or 0 at 120 Hz. frameCount
 * still changes at most once per render, so code that uses it as a "once per frame"
 * marker (Game_Interpreter.checkFreeze, VE_BasicModule) keeps working.
 */
(function () {
    'use strict';

    var interval = 0;
    var tolerance = 0;
    var nextFrame = 0;
    var stepsSinceRender = 0;

    MvPlayer.setFrameRateCap = function (cap) {
        cap = Math.max(0, Number(cap) || 0);
        interval = cap > 0 ? 1000 / cap : 0;
        // rAF timestamps jitter slightly; accept a frame a quarter interval early.
        tolerance = interval / 4;
        nextFrame = 0;
        MvPlayer.frameIntervalMs = interval;
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
            var start = performance.now();
            self.update();
            if (MvPlayer.perf) MvPlayer.perf.recordWork(performance.now() - start);
        });
    };

    // Logic steps of a running scene (loading screens do not count, as in vanilla MV).
    var _updateScene = SceneManager.updateScene;
    SceneManager.updateScene = function () {
        var result = _updateScene.apply(this, arguments);
        if (this.isCurrentSceneStarted()) stepsSinceRender++;
        return result;
    };

    var _render = Graphics.render;
    Graphics.render = function () {
        var before = this.frameCount;
        var result = _render.apply(this, arguments);
        // Vanilla adds 1 per render; count the logic steps instead.
        if (this.frameCount === before + 1) this.frameCount = before + stepsSinceRender;
        stepsSinceRender = 0;
        return result;
    };

    MvPlayer.setFrameRateCap(MvPlayer.config.frameRateCap || 0);
})();
