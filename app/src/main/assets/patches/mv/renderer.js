// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: renderer choice. Runs after rpg_*.js, before plugins (and before debug.js,
 * whose renderer log wraps this one).
 * Patch version: 2
 *
 * config.renderer:
 *   'webgl'  (default) PIXI's auto detection, i.e. WebGL with a Canvas fallback. On mobile
 *            MV would pick Canvas, which breaks Mode 7, lighting and filter plugins.
 *   'canvas' forces PIXI's Canvas renderer.
 *   'webgl2' (experimental) asks for a WebGL 2 context for the game canvas. PIXI 4 is a
 *            WebGL 1 renderer; WebGL 2 runs its GLSL ES 1.0 shaders, but some WebGL 1
 *            extensions are core there. If the renderer cannot be created, the canvas is
 *            replaced and WebGL 1 is used instead.
 * MvPlayer.renderer = { requested, type: 'webgl2' | 'webgl' | 'canvas', gpu, fallback }.
 *
 * Shader precision: PIXI compiles fragment shaders with mediump by default. On Mali GPUs
 * mediump is a 16-bit float, too coarse for the pixel coordinates of large tileset
 * images, so the tilemap samples the neighbouring texel and thin lines appear between
 * tiles. When the GPU supports highp in fragment shaders (almost all do), PIXI uses it.
 */
(function () {
    'use strict';

    var requested = MvPlayer.config.renderer || 'webgl';
    var upgradeCanvas = null;

    SceneManager.shouldUseCanvasRenderer = function () {
        return requested === 'canvas';
    };

    /**
     * The player's choice wins over the game's. Plugins such as Community_Basic (shipped
     * with MV, so in many games) replace SceneManager.preferableRendererType after this
     * patch ran, so the type is enforced where the renderer is created.
     */
    function rendererType(gameType) {
        if (requested === 'canvas') return 'canvas';
        return gameType === 'canvas' ? 'auto' : gameType;
    }

    // Only the game canvas is upgraded; PIXI's feature test and plugins keep WebGL 1.
    var getContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (type, attributes) {
        if (this === upgradeCanvas && (type === 'webgl' || type === 'experimental-webgl')) {
            var context = getContext.call(this, 'webgl2', attributes);
            if (context) return context;
        }
        return getContext.apply(this, arguments);
    };

    function contextType(renderer) {
        if (!renderer) return null;
        if (!renderer.gl) return 'canvas';
        return typeof WebGL2RenderingContext !== 'undefined' && renderer.gl instanceof WebGL2RenderingContext
            ? 'webgl2' : 'webgl';
    }

    function gpuName(gl) {
        if (!gl) return null;
        var ext = gl.getExtension('WEBGL_debug_renderer_info');
        return ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
    }

    /** A canvas that already has a WebGL 2 context cannot give a WebGL 1 one; swap it. */
    function replaceCanvas() {
        var old = Graphics._canvas;
        var canvas = document.createElement('canvas');
        canvas.id = old.id;
        if (old.parentNode) old.parentNode.replaceChild(canvas, old);
        Graphics._canvas = canvas;
        Graphics._updateCanvas();
    }

    /** Fragment shader precision support, read from a throwaway WebGL context. */
    function fragmentPrecision() {
        var canvas = document.createElement('canvas');
        var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
        if (!gl) return null;
        var high = gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.HIGH_FLOAT);
        var medium = gl.getShaderPrecisionFormat(gl.FRAGMENT_SHADER, gl.MEDIUM_FLOAT);
        var info = { highp: !!(high && high.precision > 0), mediumBits: medium ? medium.precision : null };
        var lose = gl.getExtension('WEBGL_lose_context');
        if (lose) lose.loseContext();
        return info;
    }

    function useHighPrecision() {
        if (requested === 'canvas' || !window.PIXI || !PIXI.settings) return;
        var info = fragmentPrecision();
        if (!info) return;
        if (info.highp) PIXI.settings.PRECISION_FRAGMENT = 'highp';
        MvPlayer.log('i', 'Renderer', 'Fragment precision: highp ' + (info.highp ? 'used' : 'not supported') +
            ', mediump has ' + info.mediumBits + ' bits');
    }

    var _createRenderer = Graphics._createRenderer;
    Graphics._createRenderer = function () {
        var fallback = null;
        // Before the renderer exists: its plugins (the tilemap) compile shaders on creation.
        useHighPrecision();
        var gameType = this._rendererType;
        this._rendererType = rendererType(gameType);
        if (this._rendererType !== gameType) {
            MvPlayer.log('i', 'Renderer', 'Game asked for ' + gameType + ', using ' + this._rendererType);
        }
        if (requested === 'webgl2') {
            upgradeCanvas = this._canvas;
            try {
                _createRenderer.apply(this, arguments);
            } finally {
                upgradeCanvas = null;
            }
            if (contextType(this._renderer) !== 'webgl2') {
                fallback = 'WebGL 2 renderer could not be created';
                MvPlayer.log('w', 'Renderer', fallback + ', using WebGL');
                if (this._renderer && this._renderer.destroy) this._renderer.destroy();
                this._renderer = null;
                replaceCanvas();
                _createRenderer.apply(this, arguments);
            }
        } else {
            _createRenderer.apply(this, arguments);
        }
        var gl = this._renderer && this._renderer.gl;
        MvPlayer.renderer = {
            requested: requested,
            type: contextType(this._renderer),
            gpu: gpuName(gl),
            fallback: fallback
        };
        if (gl) checkErrorsSoon(gl);
    };

    // Report GL errors from the first frames, for every WebGL renderer: games and PIXI
    // cause some on WebGL 1 too (e.g. 0x502 in Fear & Hunger), so a comparison needs both.
    function checkErrorsSoon(gl) {
        var frames = 0;
        var _render = Graphics.render;
        Graphics.render = function () {
            var result = _render.apply(this, arguments);
            if (++frames === 30) {
                Graphics.render = _render;
                var errors = [];
                for (var error = gl.getError(); error !== gl.NO_ERROR && errors.length < 8; error = gl.getError()) {
                    errors.push('0x' + error.toString(16));
                }
                MvPlayer.log('i', 'Renderer',
                    errors.length ? 'GL errors after 30 frames: ' + errors.join(', ') : 'No GL errors after 30 frames');
            }
            return result;
        };
    }
})();
