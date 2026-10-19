// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * RPGM Player: frame timing for the performance HUD and the periodic log stats.
 * Runs after rpg_*.js, before plugins (and before debug.js, which reads it).
 * Patch version: 2
 *
 * Consumers open a measurement window; every rendered frame, animation frame and logic
 * step is counted into all open windows. With no window open the hooks only check an
 * empty list, so the cost is negligible.
 *
 *   displayHz: animation frame callbacks per second (the display refresh)
 *   fps:       images actually rendered (lower with a frame rate cap)
 *   logicUps:  game logic steps (60 in MV)
 *   workMs:    JavaScript time per main-loop iteration (logic + render submission),
 *              reported by framerate.js, which owns the loop (plugins such as
 *              TDDP_FluidTimestep replace SceneManager.updateMain, so it cannot be hooked)
 *
 * MvPlayer.perf.setReporting(ms) sends a sample to host.reportStats(json) every ms
 * milliseconds (0 stops it); the host starts it from config.hud.intervalMs.
 */
(function () {
    'use strict';

    var windows = [];
    var lastRender = 0;
    var rafRunning = false;
    // Gaps longer than this are pauses or loading screens, not frames.
    var MAX_GAP_MS = 1000;
    var SLOW_GAP_MS = 34; // longer than two 60 Hz frames

    function newWindow(keepGaps) {
        return {
            start: performance.now(),
            renders: 0,
            rafs: 0,
            logic: 0,
            slow: 0,
            maxGap: 0,
            gapSum: 0,
            gapCount: 0,
            gaps: keepGaps ? [] : null,
            workSum: 0,
            workMax: 0,
            workCount: 0
        };
    }

    function rafLoop() {
        if (windows.length === 0) {
            rafRunning = false;
            return;
        }
        for (var i = 0; i < windows.length; i++) windows[i].rafs++;
        requestAnimationFrame(rafLoop);
    }

    function wrap(owner, name, makeReplacement) {
        var original = owner[name];
        if (typeof original !== 'function') {
            MvPlayer.log('w', 'Perf', 'Cannot hook ' + name + ', not a function');
            return;
        }
        owner[name] = makeReplacement(original);
    }

    wrap(Graphics, 'render', function (original) {
        return function () {
            if (windows.length) {
                var now = performance.now();
                var gap = lastRender ? now - lastRender : 0;
                lastRender = now;
                for (var i = 0; i < windows.length; i++) {
                    var w = windows[i];
                    w.renders++;
                    if (gap > 0 && gap < MAX_GAP_MS) {
                        w.gapSum += gap;
                        w.gapCount++;
                        if (gap > w.maxGap) w.maxGap = gap;
                        // Slow: longer than two frames at the current cap (at least two 60 Hz frames).
                        if (gap > Math.max(SLOW_GAP_MS, 2 * (MvPlayer.frameIntervalMs || 0))) w.slow++;
                        if (w.gaps) w.gaps.push(gap);
                    }
                }
            }
            return original.apply(this, arguments);
        };
    });

    wrap(SceneManager, 'updateScene', function (original) {
        return function () {
            for (var i = 0; i < windows.length; i++) windows[i].logic++;
            return original.apply(this, arguments);
        };
    });

    function round(value, digits) {
        return +value.toFixed(digits);
    }

    /** The frame time that 99 % of frames beat, as fps ("1 % low"). */
    function onePercentLow(gaps) {
        if (!gaps || gaps.length < 10) return null;
        var sorted = gaps.slice().sort(function (a, b) { return a - b; });
        var gap = sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * 0.99))];
        return round(1000 / gap, 1);
    }

    function imageCache() {
        var cache = ImageManager._imageCache;
        if (!cache || !cache._items) return null;
        var keys = Object.keys(cache._items);
        var pixels = 0;
        keys.forEach(function (key) {
            var bitmap = cache._items[key].bitmap;
            if (bitmap && bitmap.isReady()) pixels += bitmap.width * bitmap.height;
        });
        return { items: keys.length, megapixels: round(pixels / 1e6, 1) };
    }

    MvPlayer.perf = {
        /** Starts a measurement window; keepGaps also collects every frame gap. */
        open: function (keepGaps) {
            var w = newWindow(!!keepGaps);
            windows.push(w);
            if (!rafRunning) {
                rafRunning = true;
                requestAnimationFrame(rafLoop);
            }
            return w;
        },

        close: function (w) {
            var index = windows.indexOf(w);
            if (index >= 0) windows.splice(index, 1);
        },

        /** Statistics since the window opened or was last read; resets the window. */
        read: function (w) {
            var now = performance.now();
            var seconds = Math.max(0.001, (now - w.start) / 1000);
            var stats = {
                seconds: round(seconds, 2),
                fps: round(w.renders / seconds, 1),
                displayHz: round(w.rafs / seconds, 1),
                logicUps: round(w.logic / seconds, 1),
                frameMs: w.gapCount ? round(w.gapSum / w.gapCount, 2) : null,
                maxFrameMs: round(w.maxGap, 1),
                slowFrames: w.slow,
                low1Fps: onePercentLow(w.gaps),
                workMs: w.workCount ? round(w.workSum / w.workCount, 2) : null,
                maxWorkMs: round(w.workMax, 1),
                gaps: w.gaps ? w.gaps.map(function (g) { return round(g, 1); }) : undefined
            };
            var fresh = newWindow(!!w.gaps);
            for (var key in fresh) w[key] = fresh[key];
            w.start = now;
            return stats;
        },

        /** Memory and cache figures that do not depend on a window. */
        environment: function () {
            var env = { images: imageCache() };
            if (performance.memory) {
                env.heapMb = {
                    used: Math.round(performance.memory.usedJSHeapSize / 1048576),
                    total: Math.round(performance.memory.totalJSHeapSize / 1048576),
                    limit: Math.round(performance.memory.jsHeapSizeLimit / 1048576)
                };
            }
            if (MvPlayer.renderer) env.renderer = MvPlayer.renderer;
            env.frameIntervalMs = MvPlayer.frameIntervalMs || 0;
            env.paused = !!(MvPlayer.isPaused && MvPlayer.isPaused());
            return env;
        },

        /** Time spent in one main-loop iteration, from framerate.js. */
        recordWork: function (ms) {
            for (var i = 0; i < windows.length; i++) {
                var w = windows[i];
                w.workSum += ms;
                w.workCount++;
                if (ms > w.workMax) w.workMax = ms;
            }
        },

        /** After a pause the first frame gap is not a slow frame. */
        resetTiming: function () {
            lastRender = 0;
        }
    };

    // --- Host reporting for the performance HUD -------------------------------------------

    var reportTimer = null;
    var reportWindow = null;

    MvPlayer.perf.setReporting = function (ms) {
        if (reportTimer) clearInterval(reportTimer);
        if (reportWindow) MvPlayer.perf.close(reportWindow);
        reportTimer = null;
        reportWindow = null;
        var host = MvPlayer.host;
        ms = Number(ms) || 0;
        if (ms <= 0 || !host || !host.reportStats) return;
        reportWindow = MvPlayer.perf.open(true);
        reportTimer = setInterval(function () {
            var sample = MvPlayer.perf.read(reportWindow);
            var env = MvPlayer.perf.environment();
            for (var key in env) sample[key] = env[key];
            host.reportStats(JSON.stringify(sample));
        }, ms);
    };

    var hud = MvPlayer.config.hud || {};
    if (hud.intervalMs > 0) MvPlayer.perf.setReporting(hud.intervalMs);
})();
