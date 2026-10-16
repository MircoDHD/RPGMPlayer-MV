/*
 * MV Player: shared namespace. Runs before any game script.
 * window.MvPlayerConfig is written by the host into index.html.
 * Patch version: 1
 */
(function () {
    'use strict';

    window.MvPlayer = {
        patchVersion: 1,
        config: window.MvPlayerConfig || {},
        host: window.MvPlayerHost || null,
        log: function () {
            var args = Array.prototype.slice.call(arguments);
            console.log.apply(console, ['[MvPlayer]'].concat(args));
        }
    };
})();
