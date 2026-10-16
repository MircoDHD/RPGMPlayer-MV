/*
 * MV Player: diagnostics. Runs after rpg_*.js, before plugins.
 * Patch version: 1
 *
 * Logs engine events through MvPlayer.log into the host's session log. Hooks wrap the
 * vanilla functions, so plugins that alias them later still pass through here. Events
 * that fire very often (sound effects, video element events, per-file details) are only
 * logged in verbose mode; stats snapshots run every config.debug.statsIntervalSeconds.
 */
(function () {
    'use strict';

    var debug = MvPlayer.config.debug || {};
    var log = MvPlayer.log;

    function wrap(owner, name, makeReplacement) {
        var original = owner[name];
        if (typeof original !== 'function') {
            log('w', 'Debug', 'Cannot hook ' + name + ', not a function');
            return;
        }
        owner[name] = makeReplacement(original);
    }

    function sceneName(scene) {
        return scene ? (scene.constructor && scene.constructor.name) || 'anonymous' : 'null';
    }

    function audioName(audio) {
        if (!audio) return 'null';
        return audio.name + ' (vol ' + audio.volume + ', pitch ' + audio.pitch + ', pan ' + audio.pan + ')';
    }

    // --- Scenes -------------------------------------------------------------------------

    var sceneRequestedAt = 0;
    wrap(SceneManager, 'goto', function (original) {
        return function (sceneClass) {
            sceneRequestedAt = performance.now();
            log('i', 'Scene', 'goto ' + (sceneClass ? sceneClass.name : 'null'));
            return original.apply(this, arguments);
        };
    });

    wrap(SceneManager, 'onSceneStart', function (original) {
        return function () {
            var took = sceneRequestedAt ? Math.round(performance.now() - sceneRequestedAt) + ' ms after goto' : '';
            log('i', 'Scene', 'started ' + sceneName(this._scene) + ' ' + took);
            return original.apply(this, arguments);
        };
    });

    // --- Errors the game shows on screen --------------------------------------------------

    wrap(SceneManager, 'catchException', function (original) {
        return function (e) {
            log('e', 'GameError', 'Exception in ' + sceneName(this._scene) + ': ' + (e && e.stack ? e.stack : e));
            return original.apply(this, arguments);
        };
    });

    wrap(SceneManager, 'onError', function (original) {
        return function (e) {
            log('e', 'GameError', 'Error event: ' + (e.message || e) + ' (' + e.filename + ':' + e.lineno + ')');
            return original.apply(this, arguments);
        };
    });

    wrap(Graphics, 'printLoadingError', function (original) {
        return function (url) {
            log('e', 'Load', 'Loading error shown for ' + url);
            return original.apply(this, arguments);
        };
    });

    wrap(ResourceHandler, 'createLoader', function (original) {
        return function (url) {
            var loader = original.apply(this, arguments);
            var attempt = 0;
            return function () {
                attempt++;
                log('w', 'Load', 'Load failed, retry handler #' + attempt + ' for ' + url);
                return loader.apply(this, arguments);
            };
        };
    });

    wrap(Bitmap.prototype, '_onError', function (original) {
        return function () {
            log('w', 'Load', 'Image failed: ' + this._url);
            return original.apply(this, arguments);
        };
    });

    // --- Audio --------------------------------------------------------------------------

    ['playBgm', 'playBgs'].forEach(function (name) {
        wrap(AudioManager, name, function (original) {
            return function (audio, pos) {
                log('d', 'Audio', name + ' ' + audioName(audio) + (pos ? ' at ' + pos : ''));
                return original.apply(this, arguments);
            };
        });
    });

    wrap(AudioManager, 'playMe', function (original) {
        return function (me) {
            log('d', 'Audio', 'playMe ' + audioName(me));
            return original.apply(this, arguments);
        };
    });

    ['playSe', 'playStaticSe'].forEach(function (name) {
        wrap(AudioManager, name, function (original) {
            return function (se) {
                log('v', 'Audio', name + ' ' + audioName(se));
                return original.apply(this, arguments);
            };
        });
    });

    ['stopBgm', 'stopBgs', 'stopMe', 'stopAll', 'fadeOutBgm', 'fadeOutBgs', 'fadeInBgm', 'fadeInBgs'].forEach(function (name) {
        if (typeof AudioManager[name] !== 'function') return;
        wrap(AudioManager, name, function (original) {
            return function (duration) {
                log('d', 'Audio', name + (duration !== undefined ? ' ' + duration : ''));
                return original.apply(this, arguments);
            };
        });
    });

    // --- Video --------------------------------------------------------------------------

    wrap(Graphics, 'playVideo', function (original) {
        return function (src) {
            log('i', 'Video', 'playVideo ' + src);
            return original.apply(this, arguments);
        };
    });

    wrap(Graphics, '_createVideo', function (original) {
        return function () {
            original.apply(this, arguments);
            var video = this._video;
            if (!video) return;
            ['loadedmetadata', 'canplay', 'playing', 'pause', 'waiting', 'stalled'].forEach(function (type) {
                video.addEventListener(type, function () {
                    log('v', 'Video', type + ' t=' + video.currentTime.toFixed(2) + ' ' + video.videoWidth + 'x' + video.videoHeight);
                });
            });
            video.addEventListener('ended', function () {
                log('i', 'Video', 'ended ' + video.currentSrc);
            });
            video.addEventListener('error', function () {
                var error = video.error;
                log('e', 'Video', 'error ' + (error ? 'code ' + error.code + ' ' + error.message : '') + ' for ' + video.currentSrc);
            });
        };
    });

    // --- Maps, plugin commands, saves -------------------------------------------------------

    wrap(Game_Player.prototype, 'reserveTransfer', function (original) {
        return function (mapId, x, y, d) {
            log('i', 'Map', 'transfer to map ' + mapId + ' (' + x + ',' + y + ') dir ' + d);
            return original.apply(this, arguments);
        };
    });

    wrap(Scene_Map.prototype, 'onMapLoaded', function (original) {
        return function () {
            // The pending transfer is performed inside onMapLoaded, so log afterwards.
            var result = original.apply(this, arguments);
            log('i', 'Map', 'loaded map ' + $gameMap.mapId() + ' "' + ($dataMap && $dataMap.displayName || '') + '" ' +
                ($dataMap ? $dataMap.width + 'x' + $dataMap.height + ', ' + $dataMap.events.length + ' events' : ''));
            return result;
        };
    });

    wrap(Game_Interpreter.prototype, 'pluginCommand', function (original) {
        return function (command, args) {
            log('d', 'PluginCmd', command + ' ' + (args || []).join(' ') + ' (map ' + this._mapId + ', event ' + this._eventId + ')');
            return original.apply(this, arguments);
        };
    });

    ['saveGame', 'loadGame'].forEach(function (name) {
        wrap(DataManager, name, function (original) {
            return function (savefileId) {
                var start = performance.now();
                var result = original.apply(this, arguments);
                log('i', 'Save', name + ' ' + savefileId + ' -> ' + result + ' in ' + Math.round(performance.now() - start) + ' ms');
                return result;
            };
        });
    });

    // --- Boot information ------------------------------------------------------------------

    wrap(Graphics, '_createRenderer', function (original) {
        return function () {
            original.apply(this, arguments);
            var info = { renderer: this.isWebGL() ? 'webgl' : 'canvas', size: this._width + 'x' + this._height };
            var gl = this._renderer && this._renderer.gl;
            if (gl) {
                var ext = gl.getExtension('WEBGL_debug_renderer_info');
                info.gpu = ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
                info.gpuVendor = ext ? gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR);
                info.glVersion = gl.getParameter(gl.VERSION);
                info.maxTexture = gl.getParameter(gl.MAX_TEXTURE_SIZE);
            }
            log('i', 'Boot', 'Renderer', info);
        };
    });

    (function logEnvironment() {
        var video = document.createElement('video');
        var audio = document.createElement('audio');
        log('i', 'Boot', 'Environment', {
            userAgent: navigator.userAgent,
            devicePixelRatio: window.devicePixelRatio,
            window: window.innerWidth + 'x' + window.innerHeight,
            screen: screen.width + 'x' + screen.height,
            cores: navigator.hardwareConcurrency,
            deviceMemoryGb: navigator.deviceMemory,
            mvVersion: Utils.RPGMAKER_VERSION,
            isMobileDevice: Utils.isMobileDevice(),
            canPlay: {
                ogg: audio.canPlayType('audio/ogg; codecs="vorbis"'),
                m4a: audio.canPlayType('audio/mp4; codecs="mp4a.40.2"'),
                webm: video.canPlayType('video/webm; codecs="vp8, vorbis"'),
                webmVp9: video.canPlayType('video/webm; codecs="vp9"'),
                mp4: video.canPlayType('video/mp4; codecs="avc1.42E01E"')
            }
        });
    })();

    wrap(WebAudio, '_createContext', function (original) {
        return function () {
            original.apply(this, arguments);
            var context = this._context;
            if (context) {
                log('i', 'Boot', 'AudioContext', {
                    state: context.state,
                    sampleRate: context.sampleRate,
                    baseLatency: context.baseLatency,
                    outputLatency: context.outputLatency
                });
            }
        };
    });

    // --- Periodic stats ---------------------------------------------------------------------

    var interval = (debug.statsIntervalSeconds || 0) * 1000;
    if (interval > 0) {
        var rafFrames = 0;
        var slowFrames = 0;
        var maxGap = 0;
        var lastRaf = 0;
        var tick = function (now) {
            if (lastRaf) {
                var gap = now - lastRaf;
                if (gap > maxGap) maxGap = gap;
                if (gap > 34) slowFrames++; // more than two 60 Hz frames
            }
            lastRaf = now;
            rafFrames++;
            requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);

        var lastTime = performance.now();
        var lastGameFrames = Graphics.frameCount;

        var imageCacheStats = function () {
            var cache = ImageManager._imageCache;
            if (!cache || !cache._items) return null;
            var keys = Object.keys(cache._items);
            var pixels = 0;
            keys.forEach(function (key) {
                var bitmap = cache._items[key].bitmap;
                if (bitmap && bitmap.isReady()) pixels += bitmap.width * bitmap.height;
            });
            return { items: keys.length, megapixels: +(pixels / 1e6).toFixed(1) };
        };

        setInterval(function () {
            var now = performance.now();
            var seconds = (now - lastTime) / 1000;
            var stats = {
                fps: +(rafFrames / seconds).toFixed(1),
                gameFps: +((Graphics.frameCount - lastGameFrames) / seconds).toFixed(1),
                slowFrames: slowFrames,
                maxFrameMs: Math.round(maxGap),
                scene: sceneName(SceneManager._scene),
                map: typeof $gameMap !== 'undefined' && $gameMap ? $gameMap.mapId() : null,
                playtime: typeof $gameSystem !== 'undefined' && $gameSystem ? $gameSystem.playtimeText() : null,
                images: imageCacheStats(),
                visibility: document.visibilityState
            };
            if (performance.memory) {
                stats.heapMb = {
                    used: Math.round(performance.memory.usedJSHeapSize / 1048576),
                    total: Math.round(performance.memory.totalJSHeapSize / 1048576),
                    limit: Math.round(performance.memory.jsHeapSizeLimit / 1048576)
                };
            }
            if (MvPlayer.audioReport) stats.audio = MvPlayer.audioReport();
            log('i', 'Stats', stats);
            rafFrames = 0;
            slowFrames = 0;
            maxGap = 0;
            lastTime = now;
            lastGameFrames = Graphics.frameCount;
        }, interval);
    }
})();
