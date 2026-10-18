// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * MV Player: native text input for name entry. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * When MV's name input scene (Scene_Name, event command "Name Input Processing") starts,
 * the host opens an Android text field so the soft keyboard can be used. The typed name
 * is written into the scene's edit window and confirmed like pressing "OK" on the grid.
 * Cancelling the dialog keeps the game's own character grid. Hooks the engine scene, so
 * it works regardless of plugins that only add keyboard listeners (e.g. DK_Name_Input).
 */
(function () {
    'use strict';

    var host = MvPlayer.host;
    if (typeof Scene_Name === 'undefined' || !host || !host.requestTextInput) return;

    var activeScene = null;

    var _Scene_Name_start = Scene_Name.prototype.start;
    Scene_Name.prototype.start = function () {
        _Scene_Name_start.apply(this, arguments);
        activeScene = this;
        var edit = this._editWindow;
        var maxLength = this._maxLength || (edit && edit._maxLength) || 8;
        MvPlayer.log('i', 'TextInput', 'Name input for actor ' + this._actorId + ', max ' + maxLength);
        host.requestTextInput(edit ? edit.name() : '', maxLength);
    };

    var _Scene_Name_terminate = Scene_Name.prototype.terminate;
    Scene_Name.prototype.terminate = function () {
        if (activeScene === this) activeScene = null;
        return _Scene_Name_terminate.apply(this, arguments);
    };

    /** Called by the host with the typed text, or null when the player chose the grid. */
    MvPlayer.onTextInput = function (text) {
        var scene = activeScene;
        if (!scene || SceneManager._scene !== scene || text === null || text === undefined) return;
        var edit = scene._editWindow;
        if (!edit) return;
        var maxLength = scene._maxLength || edit._maxLength || 8;
        var name = String(text).substring(0, maxLength);
        edit._name = name;
        edit._index = name.length;
        edit.refresh();
        scene.onInputOk();
        MvPlayer.log('i', 'TextInput', 'Name set (' + name.length + ' characters)');
    };
})();
