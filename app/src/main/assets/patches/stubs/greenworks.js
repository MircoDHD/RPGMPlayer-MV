// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: no-op Steam (greenworks) module. Runs after rpg_*.js, before plugins.
 * Patch version: 2
 *
 * Steam plugins such as Archeia_Steamworks call require('./js/libs/greenworks') at load
 * time, which throws in a browser and leaves the plugin half-initialised. This registers
 * a greenworks module with the require() of mv/nwjs.js; initAPI() reports that Steam is
 * not running, so the plugins skip achievements and cloud saves.
 */
(function () {
    'use strict';

    function no() {
        return false;
    }

    var greenworks = {
        initAPI: no,
        init: no,
        isSteamRunning: no,
        isCloudEnabled: no,
        isCloudEnabledForUser: no,
        isGameOverlayEnabled: no,
        getAppId: function () { return 0; },
        getSteamId: function () {
            return { screenName: '', steamId: '0', accountId: 0, staticAccountId: '0', isValid: 0, level: 0 };
        },
        getCurrentGameLanguage: function () { return 'english'; },
        getCurrentUILanguage: function () { return 'english'; },
        getAchievementNames: function () { return []; },
        getNumberOfAchievements: function () { return 0; }
    };

    // Unknown methods become no-ops instead of "undefined is not a function".
    var module = typeof Proxy === 'function'
        ? new Proxy(greenworks, {
            get: function (target, key) {
                if (key in target) return target[key];
                if (typeof key === 'symbol' || key === 'then') return undefined;
                return no;
            }
        })
        : greenworks;

    if (MvPlayer.node) {
        MvPlayer.node.register(/greenworks/i, module);
    } else if (typeof window.require === 'undefined') {
        window.require = function (name) {
            if (/greenworks/i.test(String(name))) return module;
            var error = new Error("Cannot find module '" + name + "'");
            error.code = 'MODULE_NOT_FOUND';
            throw error;
        };
    }
})();
