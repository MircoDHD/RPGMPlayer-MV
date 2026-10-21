// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: font override. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * config.font = { url, scale } or null (game font at its own size). MV draws all text with
 * Bitmap.fontFace, 'GameFont' by default (the game's fonts/gamefont.css). Where a font
 * face names GameFont, the chosen font replaces it and the size is scaled; fonts that
 * plugins pick by their own names stay. Each loaded font gets a new family name, so a
 * change at runtime (MvPlayer.fonts.configure) cannot mix old and new glyphs; the
 * visible windows of the current scene are redrawn with it.
 * Scene_Boot waits for the font, as it waits for the game font.
 */
(function () {
    'use strict';

    var FAMILY = 'RPGMPlayerFont';
    var family = null;     // loaded family name, null = game font
    var scale = 1;
    var loading = false;
    var version = 0;

    /** Redraws the open windows after a change while the game runs. */
    function refreshWindows() {
        var scene = typeof SceneManager !== 'undefined' && SceneManager._scene;
        var layer = scene && scene._windowLayer;
        if (!layer) return;
        layer.children.forEach(function (win) {
            if (!win.visible || typeof win.refresh !== 'function') return;
            try {
                win.refresh();
            } catch (e) {
                MvPlayer.log('w', 'Fonts', 'Could not redraw ' + (win.constructor && win.constructor.name) + ': ' + e);
            }
        });
    }

    function configure(font) {
        var v = ++version;
        if (!font || !font.url) {
            family = null;
            scale = font && font.scale ? font.scale : 1;
            loading = false;
            refreshWindows();
            return;
        }
        if (typeof FontFace === 'undefined' || !document.fonts) {
            MvPlayer.log('w', 'Fonts', 'FontFace API missing; keeping the game font');
            return;
        }
        loading = true;
        var name = FAMILY + v;
        new FontFace(name, 'url("' + font.url + '")').load().then(function (face) {
            if (v !== version) return;
            document.fonts.add(face);
            family = name;
            scale = font.scale || 1;
            loading = false;
            MvPlayer.log('i', 'Fonts', 'Using font ' + font.url + ' at ' + Math.round(scale * 100) + ' %');
            refreshWindows();
        }, function (e) {
            if (v !== version) return;
            loading = false;
            family = null;
            MvPlayer.log('e', 'Fonts', 'Could not load font ' + font.url + ', keeping the game font: ' + e);
        });
    }

    var makeFontNameText = Bitmap.prototype._makeFontNameText;
    Bitmap.prototype._makeFontNameText = function () {
        var face = this.fontFace;
        if ((!family && scale === 1) || typeof face !== 'string' || face.indexOf('GameFont') < 0) {
            return makeFontNameText.apply(this, arguments);
        }
        var size = this.fontSize;
        if (family) this.fontFace = face.replace(/GameFont/g, family);
        this.fontSize = Math.round(size * scale);
        try {
            return makeFontNameText.apply(this, arguments);
        } finally {
            this.fontFace = face;
            this.fontSize = size;
        }
    };

    var bootIsReady = Scene_Boot.prototype.isReady;
    Scene_Boot.prototype.isReady = function () {
        return !loading && bootIsReady.apply(this, arguments);
    };

    MvPlayer.fonts = { configure: configure };
    configure(MvPlayer.config.font);
})();
