// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: error handling. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * MV stops on any uncaught error and shows it on a blurred screen. The host now gets every
 * such error (host.onGameError) and offers to continue; MvPlayer.errors.resume() hides the
 * error screen and restarts the game loop.
 *
 * config.continueAfterErrors (MvPlayer.errors.setContinue at runtime): errors are logged
 * and reported without stopping, and images or audio that fail to load are skipped (an
 * image becomes an empty 1x1 bitmap) instead of stopping with "Failed to load". If errors
 * keep coming (more than REPEAT_LIMIT within REPEAT_WINDOW ms, i.e. every frame), MV's
 * normal stop takes over, so a broken loop cannot run on silently.
 *
 * Installed on window load, after the plugins, so plugin replacements of these methods are
 * wrapped too; SceneManager binds onError when the game starts, which is later.
 */
(function () {
    'use strict';

    var REPEAT_LIMIT = 30;
    var REPEAT_WINDOW = 2000;

    var continueMode = !!MvPlayer.config.continueAfterErrors;
    var recent = [];
    var reported = {};
    var skipped = {};
    // Whether a frame is already requested (the loop is alive); see ensureLoop.
    var frameRequested = false;

    function describe(e) {
        if (e instanceof Error) return { name: e.name, message: e.message, stack: e.stack || '' };
        if (e && e.message) return { name: 'Error', message: String(e.message), stack: e.filename ? e.filename + ':' + e.lineno : '' };
        return { name: 'UnknownError', message: String(e), stack: '' };
    }

    function notify(info, stopped) {
        var host = MvPlayer.host;
        if (!host || !host.onGameError) return;
        // Continued errors are shown once per message; a stop is always shown.
        if (!stopped) {
            if (reported[info.message]) return;
            reported[info.message] = true;
        }
        host.onGameError(JSON.stringify({ name: info.name, message: info.message, stopped: stopped }));
    }

    function tooMany() {
        var now = Date.now();
        recent.push(now);
        while (recent.length && now - recent[0] > REPEAT_WINDOW) recent.shift();
        return recent.length > REPEAT_LIMIT;
    }

    function skip(kind, url) {
        if (skipped[url]) return;
        skipped[url] = true;
        MvPlayer.log('w', 'Errors', 'Skipping ' + kind + ' that failed to load: ' + url);
    }

    /** Requests the next frame unless one is already pending, so the loop never runs twice. */
    function ensureLoop() {
        if (!frameRequested) SceneManager.requestUpdate();
    }

    /** An image that failed becomes an empty, loaded 1x1 bitmap. */
    function blank(bitmap) {
        if (typeof bitmap._createCanvas !== 'function' || typeof bitmap._createBaseTexture !== 'function') return;
        try {
            bitmap._image = null;
            bitmap._createCanvas(1, 1);
            bitmap._createBaseTexture(bitmap.__canvas);
            bitmap._loadingState = 'loaded';
            if (bitmap._callLoadListeners) bitmap._callLoadListeners();
        } catch (e) {
            MvPlayer.log('w', 'Errors', 'Could not replace missing image ' + bitmap._url + ': ' + e);
        }
    }

    function install() {
        var requestUpdate = SceneManager.requestUpdate;
        SceneManager.requestUpdate = function () {
            if (!this._stopped) frameRequested = true;
            return requestUpdate.apply(this, arguments);
        };

        var update = SceneManager.update;
        SceneManager.update = function () {
            frameRequested = false;
            return update.apply(this, arguments);
        };

        var catchException = SceneManager.catchException;
        SceneManager.catchException = function (e) {
            var info = describe(e);
            if (continueMode && !tooMany()) {
                MvPlayer.log('w', 'Errors', 'Continuing after ' + info.name + ': ' + info.message + '\n' + info.stack);
                notify(info, false);
                // The error skipped the end of updateMain, where the next frame is requested.
                ensureLoop();
                return;
            }
            catchException.apply(this, arguments);
            notify(info, true);
        };

        var onError = SceneManager.onError;
        SceneManager.onError = function (e) {
            var info = describe(e);
            if (continueMode && !tooMany()) {
                MvPlayer.log('w', 'Errors', 'Continuing after error event: ' + info.message + ' (' + info.stack + ')');
                notify(info, false);
                return;
            }
            onError.apply(this, arguments);
            notify(info, true);
        };

        // Local files that failed once will fail again; skip MV's retries and error screen.
        if (typeof ResourceHandler !== 'undefined') {
            var createLoader = ResourceHandler.createLoader;
            ResourceHandler.createLoader = function (url, retryMethod, resignMethod) {
                var loader = createLoader.apply(this, arguments);
                return function () {
                    if (!continueMode) return loader.apply(this, arguments);
                    skip('file', url);
                    if (resignMethod) resignMethod();
                };
            };
        }

        var bitmapOnError = Bitmap.prototype._onError;
        Bitmap.prototype._onError = function () {
            var result = bitmapOnError.apply(this, arguments);
            if (continueMode) {
                skip('image', this._url);
                blank(this);
            }
            return result;
        };

        var checkWebAudioError = AudioManager.checkWebAudioError;
        AudioManager.checkWebAudioError = function (webAudio) {
            if (continueMode && webAudio && webAudio.isError && webAudio.isError()) {
                skip('audio', webAudio.url);
                return;
            }
            return checkWebAudioError.apply(this, arguments);
        };
    }

    MvPlayer.errors = {
        setContinue: function (enabled) {
            continueMode = !!enabled;
            MvPlayer.log('i', 'Errors', 'Continue after errors ' + (continueMode ? 'on' : 'off'));
        },

        /** Hides MV's error screen and restarts the game loop. */
        resume: function () {
            MvPlayer.log('w', 'Errors', 'Continuing after a stop, as the player chose');
            Graphics._errorShowed = false;
            if (Graphics._errorPrinter) Graphics._errorPrinter.innerHTML = '';
            var canvas = Graphics._canvas;
            if (canvas) {
                canvas.style.opacity = '';
                canvas.style.filter = '';
                canvas.style.webkitFilter = '';
            }
            // The picture settings may use a CSS filter of their own.
            if (MvPlayer.picture) MvPlayer.picture.configure(MvPlayer.picture.settings);
            recent = [];
            SceneManager._stopped = false;
            ensureLoop();
        },
    };

    window.addEventListener('load', install);
})();
