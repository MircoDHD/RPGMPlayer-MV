// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: save files through the host bridge. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * MV only writes real files under NW.js (via require('fs')). Here StorageManager runs in
 * "local file" mode and the host stores the files in www/save/, byte-identical to the
 * PC version (LZString base64), so saves can be copied between both.
 */
(function () {
    'use strict';

    var host = MvPlayer.host;

    // Respect plugins that rename save files: take the file name from localFilePath().
    function fileName(savefileId) {
        return String(StorageManager.localFilePath(savefileId)).split(/[\\/]/).pop();
    }

    function read(name) {
        var data = host.saveRead(name);
        return data === undefined ? null : data;
    }

    function write(name, data) {
        if (!host.saveWrite(name, data)) {
            throw new Error('Failed to write save file ' + name);
        }
    }

    StorageManager.isLocalMode = function () {
        return true;
    };

    StorageManager.localFileDirectoryPath = function () {
        return 'save/';
    };

    StorageManager.saveToLocalFile = function (savefileId, json) {
        write(fileName(savefileId), LZString.compressToBase64(json));
    };

    StorageManager.loadFromLocalFile = function (savefileId) {
        return LZString.decompressFromBase64(read(fileName(savefileId)));
    };

    StorageManager.loadFromLocalBackupFile = function (savefileId) {
        return LZString.decompressFromBase64(read(fileName(savefileId) + '.bak'));
    };

    StorageManager.localFileExists = function (savefileId) {
        return host.saveExists(fileName(savefileId));
    };

    StorageManager.localFileBackupExists = function (savefileId) {
        return host.saveExists(fileName(savefileId) + '.bak');
    };

    StorageManager.removeLocalFile = function (savefileId) {
        host.saveRemove(fileName(savefileId));
    };

    // The vanilla versions of these three call require('fs') directly in local mode.
    StorageManager.backup = function (savefileId) {
        if (this.exists(savefileId)) {
            var name = fileName(savefileId);
            write(name + '.bak', read(name));
        }
    };

    StorageManager.cleanBackup = function (savefileId) {
        if (this.backupExists(savefileId)) {
            host.saveRemove(fileName(savefileId) + '.bak');
        }
    };

    StorageManager.restoreBackup = function (savefileId) {
        if (this.backupExists(savefileId)) {
            var name = fileName(savefileId);
            write(name, read(name + '.bak'));
            host.saveRemove(name + '.bak');
        }
    };
})();
