/*
 * MV Player: no-op Steam (greenworks) module. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * Steam plugins such as Archeia_Steamworks call require('./js/libs/greenworks') at load
 * time, which throws in a browser and leaves the plugin half-initialised. This provides a
 * global require() that only knows greenworks; initAPI() reports that Steam is not
 * running, so the plugins skip achievements and cloud saves. Utils.isNwjs() stays false
 * because `process` is still undefined.
 */
(function () {
    'use strict';

    if (typeof window.require !== 'undefined') return;

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

    window.require = function (name) {
        if (/greenworks/i.test(String(name))) {
            MvPlayer.log('Providing greenworks stub for', name);
            return module;
        }
        var error = new Error("Cannot find module '" + name + "'");
        error.code = 'MODULE_NOT_FOUND';
        throw error;
    };
})();
