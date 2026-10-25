// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: app saves ("Stände"). Runs after rpg_*.js, before plugins.
 * Patch version: 3
 *
 * Every config.appSaves.intervalMinutes of active play (the game running in the
 * foreground, not paused) the current state is saved through the host. It waits for a
 * calm moment on the map: no transfer, no running event, no message. The content is what
 * MV puts into a save file (DataManager.makeSaveContents, JsonEx), so plugins that add
 * their own data to saves are covered. The host compresses it: LZString here would stall
 * the game for up to a second on slow devices. Nothing changes in the game: the
 * in-game save count stays as it was, and the game's own save files are not touched.
 * When the app goes to the background after at least LEAVE_MIN_MS of play, a point is
 * saved right away if the moment is calm: phones with little memory end the process in
 * the background, which would lose up to a whole interval of play.
 * The ids of the active mods (config.appSaves.mods) are stored with each point.
 *
 * MvPlayer.appSaves.load(id) loads a point like MV's load screen does. With
 * config.appSaves.loadOnStart the game skips its title and loads that point once the boot
 * scene has read the database.
 */
(function () {
    'use strict';

    var options = MvPlayer.config.appSaves || {};
    var host = MvPlayer.host;
    var CHECK_MS = 2000;
    var THUMB_WIDTH = 192;
    var LEAVE_MIN_MS = 2 * 60000;

    var mods = JSON.stringify(options.mods || []);
    var enabled = !!options.enabled;
    var intervalMs = (options.intervalMinutes || 15) * 60000;
    var played = 0;        // active play since the last point, ms
    var lastTick = 0;
    var lastCheck = 0;

    function isPlaying() {
        var scene = SceneManager._scene;
        return !!scene && typeof $gameSystem !== 'undefined' && $gameSystem &&
            !(scene instanceof Scene_Boot) && !(scene instanceof Scene_Title) && !(scene instanceof Scene_Gameover);
    }

    /** A moment where saving and later loading gives the same situation back. */
    function isCalm() {
        var scene = SceneManager._scene;
        return scene instanceof Scene_Map && scene.isActive() && !SceneManager.isSceneChanging() &&
            !$gamePlayer.isTransferring() && !$gameMap.isEventRunning() && !$gameMessage.isBusy() &&
            !$gamePlayer.isMoving();
    }

    function locationName() {
        var name = $gameMap.displayName && $gameMap.displayName();
        if (!name && typeof $dataMapInfos !== 'undefined' && $dataMapInfos[$gameMap.mapId()]) {
            name = $dataMapInfos[$gameMap.mapId()].name;
        }
        return name || '';
    }

    function thumbnail() {
        try {
            var snap = SceneManager.snap();
            var canvas = document.createElement('canvas');
            canvas.width = THUMB_WIDTH;
            canvas.height = Math.round(THUMB_WIDTH * snap.height / snap.width);
            canvas.getContext('2d').drawImage(snap._canvas || snap.canvas, 0, 0, canvas.width, canvas.height);
            var url = canvas.toDataURL('image/webp', 0.7);
            return url.indexOf('base64,') > 0 ? url.split('base64,')[1] : '';
        } catch (e) {
            MvPlayer.log('w', 'AppSaves', 'No screenshot for the app save: ' + e);
            return '';
        }
    }

    /** Saves the current state through the host; true on success. */
    function create() {
        if (!host || !host.appSaveWrite) return false;
        var start = performance.now();
        try {
            // onBeforeSave stores the music to resume with; the save count stays unchanged.
            var saveCount = $gameSystem._saveCount;
            $gameSystem.onBeforeSave();
            $gameSystem._saveCount = saveCount;
            var json = JsonEx.stringify(DataManager.makeSaveContents());
            var ok = host.appSaveWrite(json, thumbnail(), $gameSystem.playtimeText(), locationName(), mods);
            MvPlayer.log('i', 'AppSaves', 'App save ' + (ok ? 'written' : 'FAILED') + ': ' + json.length + ' chars of JSON in ' +
                Math.round(performance.now() - start) + ' ms');
            return ok;
        } catch (e) {
            MvPlayer.log('e', 'AppSaves', 'Could not create an app save: ' + (e && e.stack || e));
            return false;
        }
    }

    /** Loads a point like Scene_Load does. */
    function load(id) {
        var json = host && host.appSaveRead ? host.appSaveRead(id) : null;
        if (!json) {
            MvPlayer.log('e', 'AppSaves', 'App save ' + id + ' not found');
            return false;
        }
        try {
            var contents = JsonEx.parse(json);
            AudioManager.stopAll();
            DataManager.createGameObjects();
            DataManager.extractSaveContents(contents);
            if ($gameSystem.versionId() !== $dataSystem.versionId) {
                $gamePlayer.reserveTransfer($gameMap.mapId(), $gamePlayer.x, $gamePlayer.y);
                $gamePlayer.requestMapReload();
            }
            SceneManager.goto(Scene_Map);
            $gameSystem.onAfterLoad();
            played = 0;
            MvPlayer.log('i', 'AppSaves', 'Loaded app save ' + id + ' (' + $gameSystem.playtimeText() + ')');
            return true;
        } catch (e) {
            MvPlayer.log('e', 'AppSaves', 'Could not load app save ' + id + ': ' + (e && e.stack || e));
            return false;
        }
    }

    function tick() {
        var now = performance.now();
        var delta = lastTick ? now - lastTick : 0;
        lastTick = now;
        // Longer gaps are pauses (menu, background), not play.
        if (enabled && delta > 0 && delta < 1000 && isPlaying()) played += delta;
        if (played < intervalMs || now - lastCheck < CHECK_MS) return;
        lastCheck = now;
        if (isCalm() && create()) played = 0;
    }

    /** The app goes to the background: save now unless the last point is recent. */
    function onLeave() {
        if (!enabled || played < LEAVE_MIN_MS || !isPlaying() || !isCalm()) return false;
        MvPlayer.log('i', 'AppSaves', 'Leaving the app: saving now');
        if (!create()) return false;
        played = 0;
        return true;
    }

    function install() {
        var update = SceneManager.update;
        SceneManager.update = function () {
            var result = update.apply(this, arguments);
            tick();
            return result;
        };

        if (options.loadOnStart) {
            var bootStart = Scene_Boot.prototype.start;
            Scene_Boot.prototype.start = function () {
                bootStart.apply(this, arguments);
                if (!load(options.loadOnStart)) MvPlayer.log('w', 'AppSaves', 'Starting normally instead');
            };
        }
    }

    MvPlayer.appSaves = {
        create: create,
        load: load,
        onLeave: onLeave,
        setEnabled: function (on) {
            enabled = !!on;
            MvPlayer.log('i', 'AppSaves', 'Automatic app saves ' + (enabled ? 'on' : 'off'));
        },
        /** Active play since the last point; settable for tests. */
        get playedMs() {
            return played;
        },
        set playedMs(value) {
            played = value;
        }
    };

    // After the plugins, so their Scene_Boot and update changes are wrapped too.
    window.addEventListener('load', install);
})();
