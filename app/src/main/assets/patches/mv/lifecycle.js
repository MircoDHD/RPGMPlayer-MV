// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

/*
 * MV Player: pause and resume with the Android activity. Runs after rpg_*.js, before plugins.
 * Patch version: 2
 *
 * The host calls MvPlayer.onPause()/onResume(). While paused the main loop stops
 * rescheduling itself, the AudioContext is suspended and a playing movie is paused.
 */
(function () {
    'use strict';

    var paused = false;
    var updatePending = false;
    var videoWasPlaying = false;

    function audioContext() {
        return typeof WebAudio !== 'undefined' ? WebAudio._context : null;
    }

    function round(value, digits) {
        return typeof value === 'number' ? +value.toFixed(digits) : value;
    }

    /** Snapshot of the audio state, logged on lifecycle events and handy in DevTools. */
    MvPlayer.audioReport = function () {
        var context = audioContext();
        var master = WebAudio._masterGainNode;
        function buffer(audio) {
            return audio ? { url: audio._url, playing: audio.isPlaying(), volume: audio.volume } : null;
        }
        return {
            context: context ? context.state : null,
            time: context ? round(context.currentTime, 2) : null,
            masterGain: master ? round(master.gain.value, 3) : null,
            masterVolume: WebAudio._masterVolume,
            visibility: document.visibilityState,
            paused: paused,
            bgm: buffer(AudioManager._bgmBuffer),
            bgs: buffer(AudioManager._bgsBuffer)
        };
    };

    function logAudio(event) {
        MvPlayer.log('i', 'Audio', event, MvPlayer.audioReport());
    }

    var _WebAudio_createContext = WebAudio._createContext;
    WebAudio._createContext = function () {
        _WebAudio_createContext.apply(this, arguments);
        if (this._context && this._context.addEventListener) {
            this._context.addEventListener('statechange', function () {
                logAudio('audio state ' + WebAudio._context.state);
            });
        }
    };

    document.addEventListener('visibilitychange', function () {
        logAudio('visibility ' + document.visibilityState);
    });

    // MV fades the master gain out on hide and in on show. Pausing already suspends the
    // AudioContext, and scheduling those ramps against a stopped audio clock can leave the
    // master gain at 0 after resuming, so the fade is disabled.
    WebAudio._shouldMuteOnHide = function () {
        return false;
    };

    function restoreMasterGain() {
        var context = audioContext();
        var master = WebAudio._masterGainNode;
        if (!context || !master) return;
        master.gain.cancelScheduledValues(0);
        master.gain.setValueAtTime(WebAudio._masterVolume, context.currentTime);
    }

    var _SceneManager_requestUpdate = SceneManager.requestUpdate;
    SceneManager.requestUpdate = function () {
        if (paused) {
            updatePending = true;
            return;
        }
        _SceneManager_requestUpdate.apply(this, arguments);
    };

    // An AudioContext may start suspended until user input; resume it on the first input.
    MvPlayer.unlockAudio = function () {
        var context = audioContext();
        if (!paused && context && context.state !== 'running' && context.state !== 'closed') {
            context.resume().then(restoreMasterGain);
        }
    };

    MvPlayer.onPause = function () {
        if (paused) return;
        paused = true;
        var context = audioContext();
        if (context && context.state === 'running') {
            context.suspend();
        }
        var video = Graphics._video;
        videoWasPlaying = !!(video && !video.paused && !video.ended);
        if (videoWasPlaying) {
            video.pause();
        }
        logAudio('pause');
    };

    MvPlayer.onResume = function () {
        if (!paused) return;
        paused = false;
        var context = audioContext();
        if (context && context.state === 'running') {
            restoreMasterGain();
        } else {
            MvPlayer.unlockAudio();
        }
        if (videoWasPlaying && Graphics._video) {
            Graphics._video.play();
        }
        videoWasPlaying = false;
        // Do not let the fixed-step loop catch up on the time spent in the background.
        // Vanilla MV and frame-timing plugins like TDDP_FluidTimestep both use performance.now().
        SceneManager._currentTime = performance.now();
        SceneManager._accumulator = 0;
        if (updatePending) {
            updatePending = false;
            SceneManager.requestUpdate();
        }
        logAudio('resume');
    };
})();
