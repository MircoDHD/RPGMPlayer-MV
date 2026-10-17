// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * MV Player: engine behaviour on Android. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * Utils.isMobileDevice() stays true on purpose (stretch-to-fit, mute while hidden), but
 * it also makes MV request .m4a/.mp4 files that PC builds do not ship and forces the
 * Canvas renderer. Only those decisions are overridden here.
 */
(function () {
    'use strict';

    var config = MvPlayer.config;

    AudioManager.audioFileExt = function () {
        return config.audioExt || '.ogg';
    };

    Game_Interpreter.prototype.videoFileExt = function () {
        return config.videoExt || '.webm';
    };

    // WebGL is needed for filters, lighting and Mode 7 plugins, and is fast on current phones.
    SceneManager.shouldUseCanvasRenderer = function () {
        return false;
    };

    // On mobile MV waits for a touch before it lets videos play. The WebView allows
    // autoplay, and a controller player may never touch the screen.
    var _Graphics_initialize = Graphics.initialize;
    Graphics.initialize = function () {
        _Graphics_initialize.apply(this, arguments);
        this._videoUnlocked = true;
    };

    // MV calls window.close() to quit, which does nothing inside a WebView.
    window.close = function () {
        if (MvPlayer.host) MvPlayer.host.exitGame();
    };
})();
