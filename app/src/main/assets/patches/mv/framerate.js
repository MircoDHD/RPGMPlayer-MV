/*
 * MV Player: frame rate cap. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * MV runs its game logic at a fixed 60 steps per second, but renders once per display
 * refresh and counts rendered frames as play time (Graphics.frameCount). On a 120 Hz
 * display the play time therefore runs twice as fast and every image is drawn twice.
 * The host asks Android for a matching display rate; this gate additionally skips
 * refreshes that come too early, for displays that stay faster.
 */
(function () {
    'use strict';

    var cap = MvPlayer.config.frameRateCap || 0;
    if (cap <= 0) return;

    var interval = 1000 / cap;
    // rAF timestamps jitter slightly; accept a frame a quarter interval early.
    var tolerance = interval / 4;
    var nextFrame = 0;

    // Replaces vanilla requestUpdate (which requests the next animation frame directly).
    // lifecycle.js wraps this, so pausing still works.
    SceneManager.requestUpdate = function () {
        if (this._stopped) return;
        var self = this;
        requestAnimationFrame(function gate(now) {
            if (now < nextFrame - tolerance) {
                requestAnimationFrame(gate);
                return;
            }
            // Keep a steady cadence; resynchronise after a stall instead of catching up.
            nextFrame = now - nextFrame > interval ? now + interval : nextFrame + interval;
            self.update();
        });
    };

    MvPlayer.log('i', 'Frames', 'Frame rate capped at ' + cap + ' fps');
})();
