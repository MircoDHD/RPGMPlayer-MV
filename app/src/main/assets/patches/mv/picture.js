// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: scaling and picture filters. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * config.picture = { scaling: 'fit' | 'integer' | 'stretch', scaler: 'smooth' | 'sharp' |
 * 'pixel', sharpen, scanlines (0..1, 0 = off), brightness, contrast, saturation (1 = as is) }.
 * MvPlayer.picture.configure(picture) changes it at runtime and redraws, also while paused.
 *
 * Scaling replaces MV's (or a plugin's) screen scale. 'integer' uses whole multiples of
 * the game's pixels in device pixels; 'stretch' scales x and y separately, so the touch
 * mapping is patched to match.
 *
 * Without a filter (smooth or pixel enlargement, no effects, neutral colours) the browser
 * scales the canvas as before; 'pixel' only switches CSS image-rendering, so the default
 * costs nothing. Otherwise, on WebGL, the stage is rendered into a texture at the game's
 * size and one shader pass draws it at the screen's pixel size: sharp enlargement,
 * sharpening, scanlines and colour in a single pass. The canvas renderer has no shaders;
 * there only scaling, pixel enlargement and colour (CSS filter) apply.
 *
 * All shader code here is original. Sharp enlargement: bilinear filtering limited to one
 * screen pixel at the edge between two game pixels. Sharpening: the colour minus the
 * average of its four neighbours. Scanlines: a sine-shaped darkening towards the edges of
 * each game pixel row. Saturation uses the Rec. 709 luma weights.
 */
(function () {
    'use strict';

    var DEFAULTS = { scaling: 'fit', scaler: 'smooth', sharpen: 0, scanlines: 0, brightness: 1, contrast: 1, saturation: 1 };

    var VERTEX = [
        'attribute vec2 aVertexPosition;',
        'attribute vec2 aTextureCoord;',
        'varying vec2 vUv;',
        'void main() {',
        // Render textures hold the game upside down (row 0 at the bottom).
        '    vUv = vec2(aTextureCoord.x, 1.0 - aTextureCoord.y);',
        '    gl_Position = vec4(aVertexPosition, 0.0, 1.0);',
        '}',
    ].join('\n');

    var FRAGMENT = [
        '#ifdef GL_FRAGMENT_PRECISION_HIGH',
        'precision highp float;',
        '#else',
        'precision mediump float;',
        '#endif',
        'varying vec2 vUv;',
        'uniform sampler2D uSampler;',
        'uniform vec2 uTexSize;',
        'uniform vec2 uScale;',
        'uniform float uSharp;',
        'uniform float uSharpen;',
        'uniform float uScanlines;',
        'uniform vec3 uColour;',
        'void main() {',
        '    vec2 texel = vUv * uTexSize;',
        '    vec2 uv = vUv;',
        '    if (uSharp > 0.5) {',
        '        vec2 p = texel - 0.5;',
        '        vec2 i = floor(p);',
        '        vec2 f = clamp((p - i - 0.5) * uScale + 0.5, 0.0, 1.0);',
        '        uv = (i + f + 0.5) / uTexSize;',
        '    }',
        '    vec3 c = texture2D(uSampler, uv).rgb;',
        '    if (uSharpen > 0.0) {',
        '        vec2 d = 1.0 / uTexSize;',
        '        vec3 around = texture2D(uSampler, uv + vec2(d.x, 0.0)).rgb + texture2D(uSampler, uv - vec2(d.x, 0.0)).rgb',
        '            + texture2D(uSampler, uv + vec2(0.0, d.y)).rgb + texture2D(uSampler, uv - vec2(0.0, d.y)).rgb;',
        '        c += (c - around * 0.25) * uSharpen;',
        '    }',
        '    if (uScanlines > 0.0) {',
        '        float row = sin(3.14159265 * fract(texel.y));',
        '        c *= (1.0 - uScanlines * 0.7 * (1.0 - row)) * (1.0 + uScanlines * 0.25);',
        '    }',
        '    c *= uColour.x;',
        '    c = (c - 0.5) * uColour.y + 0.5;',
        '    c = mix(vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), c, uColour.z);',
        '    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);',
        '}',
    ].join('\n');

    var settings = normalise(MvPlayer.config.picture);
    var scaleX = 1;
    var scaleY = 1;
    var pass = null;      // shader pass state while it is in use
    var failed = false;   // the pass broke once; stay on the plain path

    function normalise(p) {
        var result = {};
        for (var key in DEFAULTS) result[key] = p && p[key] !== undefined ? p[key] : DEFAULTS[key];
        return result;
    }

    function neutralColour() {
        return settings.brightness === 1 && settings.contrast === 1 && settings.saturation === 1;
    }

    function wantsPass() {
        return !failed && Graphics.isWebGL() &&
            (settings.scaler === 'sharp' || settings.sharpen > 0 || settings.scanlines > 0 || !neutralColour());
    }

    function describe() {
        return settings.scaling + ', ' + settings.scaler + ', sharpen ' + settings.sharpen + ', scanlines ' +
            settings.scanlines + ', colour ' + [settings.brightness, settings.contrast, settings.saturation].join('/');
    }

    // --- Scaling ---------------------------------------------------------------------------

    function computeScale(g) {
        var w = window.innerWidth / g._width;
        var h = window.innerHeight / g._height;
        var dpr = window.devicePixelRatio || 1;
        if (settings.scaling === 'stretch') {
            scaleX = w;
            scaleY = h;
            return;
        }
        var fit = Math.min(w, h);
        var whole = Math.floor(fit * dpr + 0.001);
        scaleX = scaleY = settings.scaling === 'integer' && whole >= 1 ? whole / dpr : fit;
    }

    /** Size of the shown game in CSS pixels and in device pixels. */
    function outputSize() {
        var dpr = window.devicePixelRatio || 1;
        var cssWidth = Graphics._width * scaleX;
        var cssHeight = Graphics._height * scaleY;
        return { cssWidth: cssWidth, cssHeight: cssHeight, width: Math.round(cssWidth * dpr), height: Math.round(cssHeight * dpr) };
    }

    function install() {
        var g = Graphics;

        var updateRealScale = g._updateRealScale;
        g._updateRealScale = function () {
            updateRealScale.apply(this, arguments);
            computeScale(this);
            this._realScale = Math.min(scaleX, scaleY);
        };

        // Centred on whole device pixels, so integer scaling stays sharp.
        var centerElement = g._centerElement;
        g._centerElement = function (element) {
            centerElement.apply(this, arguments);
            var dpr = window.devicePixelRatio || 1;
            var gameSized = element === this._canvas || element === this._video || element === this._upperCanvas;
            var width = (gameSized ? this._width : element.width) * scaleX;
            var height = (gameSized ? this._height : element.height) * scaleY;
            element.style.width = width + 'px';
            element.style.height = height + 'px';
            element.style.margin = '0';
            element.style.right = 'auto';
            element.style.bottom = 'auto';
            element.style.left = Math.round((window.innerWidth - width) / 2 * dpr) / dpr + 'px';
            element.style.top = Math.round((window.innerHeight - height) / 2 * dpr) / dpr + 'px';
        };

        g.pageToCanvasX = function (x) {
            return this._canvas ? Math.round((x - this._canvas.offsetLeft) / scaleX) : 0;
        };
        g.pageToCanvasY = function (y) {
            return this._canvas ? Math.round((y - this._canvas.offsetTop) / scaleY) : 0;
        };

        var updateAllElements = g._updateAllElements;
        g._updateAllElements = function () {
            updateAllElements.apply(this, arguments);
            applyOutput();
        };

        var createRenderer = g._createRenderer;
        g._createRenderer = function () {
            createRenderer.apply(this, arguments);
            if (this._renderer) wrapRender(this._renderer);
            applyOutput();
        };
    }

    // --- Output ----------------------------------------------------------------------------

    /** Applies CSS look and canvas size for the current settings. */
    function applyOutput() {
        var canvas = Graphics._canvas;
        if (!canvas || !Graphics._renderer) return;
        var usePass = wantsPass();
        if (usePass !== !!pass) {
            if (usePass) startPass(); else stopPass();
        }
        var size = outputSize();
        if (pass) {
            // The canvas holds screen pixels; PIXI keeps its game-sized root target.
            if (canvas.width !== size.width || canvas.height !== size.height) {
                canvas.width = size.width;
                canvas.height = size.height;
            }
            pass.screen.resize(size.width, size.height);
        } else if (canvas.width !== Graphics._width || canvas.height !== Graphics._height) {
            canvas.width = Graphics._width;
            canvas.height = Graphics._height;
            Graphics._renderer.resize(Graphics._width, Graphics._height);
        }
        var pixel = !pass && settings.scaler !== 'smooth';
        canvas.style.imageRendering = pixel ? 'pixelated' : '';
        canvas.style.filter = !pass && !neutralColour()
            ? 'brightness(' + settings.brightness + ') contrast(' + settings.contrast + ') saturate(' + settings.saturation + ')'
            : '';
    }

    function wrapRender(renderer) {
        if (renderer._mvPictureWrapped) return;
        renderer._mvPictureWrapped = true;
        var render = renderer.render;
        renderer.render = function (displayObject, renderTexture) {
            // Only the frame for the screen goes through the pass; render textures (snapshots,
            // light maps) stay untouched.
            if (renderTexture || !pass || pass.renderer !== this) return render.apply(this, arguments);
            var args = Array.prototype.slice.call(arguments);
            args[1] = pass.texture;
            render.apply(this, args);
            present();
        };
    }

    function startPass() {
        var renderer = Graphics._renderer;
        try {
            var gl = renderer.gl;
            var shader = new PIXI.Shader(gl, VERTEX, FRAGMENT);
            var quad = new PIXI.Quad(gl, renderer.state.attribState);
            renderer.bindVao(null);
            quad.initVao(shader);
            var size = outputSize();
            pass = {
                renderer: renderer,
                context: renderer.CONTEXT_UID,
                shader: shader,
                quad: quad,
                texture: PIXI.RenderTexture.create(Graphics._width, Graphics._height),
                screen: new PIXI.RenderTarget(gl, size.width, size.height, PIXI.SCALE_MODES.LINEAR, 1, true),
            };
            MvPlayer.log('i', 'Picture', 'Filter pass on: ' + describe());
        } catch (e) {
            fail(e);
        }
    }

    function stopPass() {
        if (!pass) return;
        var old = pass;
        pass = null;
        try {
            old.texture.destroy(true);
            old.quad.vao.destroy();
            old.shader.destroy();
        } catch (e) {
            // The context may be gone; nothing left to free then.
        }
        MvPlayer.log('i', 'Picture', 'Filter pass off: ' + describe());
    }

    function fail(e) {
        failed = true;
        pass = null;
        MvPlayer.log('e', 'Picture', 'Filter pass failed, showing the game unfiltered: ' + (e && e.stack || e));
        Graphics._updateAllElements();
    }

    /** Draws the game texture to the screen with the filters. */
    function present() {
        var renderer = pass.renderer;
        if (renderer.CONTEXT_UID !== pass.context) {
            // The WebGL context was restored; rebuild everything for the new one.
            pass = null;
            startPass();
            if (!pass) return;
        }
        try {
            var gl = renderer.gl;
            var target = pass.texture.baseTexture._glRenderTargets[renderer.CONTEXT_UID];
            var shader = pass.shader;
            renderer.bindVao(pass.quad.vao);
            renderer.bindRenderTarget(pass.screen);
            renderer.bindShader(shader, false);
            var u = shader.uniforms;
            u.uSampler = 0;
            u.uTexSize = [Graphics._width, Graphics._height];
            u.uScale = [pass.screen.size.width / Graphics._width, pass.screen.size.height / Graphics._height];
            u.uSharp = settings.scaler === 'sharp' ? 1 : 0;
            u.uSharpen = settings.sharpen;
            u.uScanlines = settings.scanlines;
            u.uColour = [settings.brightness, settings.contrast, settings.saturation];
            renderer.state.setBlend(false);
            // Unit 0 is taken over, as PIXI's filter manager does.
            var empty = renderer.emptyTextures[0];
            renderer.boundTextures[0] = empty;
            gl.activeTexture(gl.TEXTURE0);
            gl.bindTexture(gl.TEXTURE_2D, target.texture.texture);
            var filter = settings.scaler === 'pixel' ? gl.NEAREST : gl.LINEAR;
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, filter);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, filter);
            pass.quad.vao.draw(gl.TRIANGLES, 6, 0);
            gl.bindTexture(gl.TEXTURE_2D, empty._glTextures[renderer.CONTEXT_UID].texture);
            renderer.state.setBlend(true);
            renderer._activeShader = null;
        } catch (e) {
            fail(e);
        }
    }

    // --- Runtime changes -------------------------------------------------------------------

    MvPlayer.picture = {
        configure: function (picture) {
            settings = normalise(picture);
            if (!Graphics._canvas) return;
            MvPlayer.log('i', 'Picture', 'Picture settings: ' + describe());
            Graphics._updateAllElements();
            // Resizing cleared the canvas; draw the current frame again, also while paused.
            var scene = SceneManager._scene;
            if (scene && Graphics._renderer) Graphics._renderer.render(scene);
        },
        get settings() {
            return settings;
        },
        get active() {
            return !!pass;
        },
    };

    // Plugins are loaded by then, so their own scaling code is wrapped too; this listener
    // runs before main.js starts the game from window.onload.
    window.addEventListener('load', install);
})();
