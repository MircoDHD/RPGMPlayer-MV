// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: shared namespace and logging. Runs before any game script.
 * window.MvPlayerConfig is written by the host into index.html.
 * Patch version: 2
 */
(function () {
    'use strict';

    var config = window.MvPlayerConfig || {};
    var host = window.MvPlayerHost || null;
    var debug = config.debug || {};

    function stringify(value) {
        if (typeof value === 'string') return value;
        if (value instanceof Error) return value.stack || String(value);
        try {
            return JSON.stringify(value);
        } catch (e) {
            return String(value);
        }
    }

    window.MvPlayer = {
        patchVersion: 2,
        config: config,
        host: host,
        verbose: !!debug.verbose,

        /**
         * level: 'v' | 'd' | 'i' | 'w' | 'e'. Extra arguments are appended, objects as JSON.
         * Goes to the host's session log; falls back to the console without a host.
         */
        log: function (level, category, message) {
            if (level === 'v' && !MvPlayer.verbose) return;
            var text = stringify(message);
            for (var i = 3; i < arguments.length; i++) text += ' ' + stringify(arguments[i]);
            if (host && host.log) {
                host.log(level, category, text);
            } else {
                var method = level === 'e' ? 'error' : level === 'w' ? 'warn' : 'log';
                console[method]('[MvPlayer:' + category + '] ' + text);
            }
        }
    };

    // Script errors, and (capture phase) failed loads of images, scripts, audio and video.
    window.addEventListener('error', function (event) {
        var target = event.target;
        if (target && target !== window && (target.src || target.href)) {
            MvPlayer.log('w', 'Resource', 'Failed to load <' + target.tagName.toLowerCase() + '> ' + (target.src || target.href));
            return;
        }
        var where = event.filename ? ' (' + event.filename + ':' + event.lineno + ':' + event.colno + ')' : '';
        MvPlayer.log('e', 'JsError', (event.message || 'Unknown error') + where +
            (event.error && event.error.stack ? '\n' + event.error.stack : ''));
    }, true);

    window.addEventListener('unhandledrejection', function (event) {
        MvPlayer.log('e', 'JsError', 'Unhandled promise rejection: ' + stringify(event.reason));
    });

    MvPlayer.log('i', 'Boot', 'Patches loaded, config ' + stringify(config));
})();
