/*
 * MV Player: pause and resume with the Android activity. Runs after rpg_*.js, before plugins.
 * Patch version: 1
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
        if (!paused && context && context.state === 'suspended') {
            context.resume();
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
    };

    MvPlayer.onResume = function () {
        if (!paused) return;
        paused = false;
        MvPlayer.unlockAudio();
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
    };
})();
