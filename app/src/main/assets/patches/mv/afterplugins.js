// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: scripts that run after the plugins. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * main.js loads the plugins through PluginManager.setup, which appends one script element
 * per plugin (async = false, so they run in order). config.afterPlugins (patch profiles and
 * the game's mvplayer-patches) are appended the same way right after them, so they run
 * after the last plugin and, like the plugins, before the game starts on window load.
 */
(function () {
    'use strict';

    var urls = MvPlayer.config.afterPlugins || [];
    if (!urls.length) return;

    var setup = PluginManager.setup;
    PluginManager.setup = function () {
        var result = setup.apply(this, arguments);
        urls.forEach(function (url) {
            var script = document.createElement('script');
            script.type = 'text/javascript';
            script.src = url;
            script.async = false;
            script.onerror = function () {
                MvPlayer.log('e', 'Patches', 'Could not load ' + url);
            };
            document.body.appendChild(script);
        });
        MvPlayer.log('i', 'Patches', 'After the plugins: ' + urls.join(', '));
        return result;
    };
})();
