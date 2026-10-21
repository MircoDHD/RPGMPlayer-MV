// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.input.InputManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.github.mircodhd.mvplayer.bridge.PlayerBridge
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.game.GameInfo
import io.github.mircodhd.mvplayer.game.GameInfoCache
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.input.GamepadInput
import io.github.mircodhd.mvplayer.input.InputAction
import io.github.mircodhd.mvplayer.input.InputRouter
import io.github.mircodhd.mvplayer.library.GameLibrary
import io.github.mircodhd.mvplayer.overlay.GamepadLayout
import io.github.mircodhd.mvplayer.overlay.LayoutNames
import io.github.mircodhd.mvplayer.overlay.LayoutRepository
import io.github.mircodhd.mvplayer.patch.PatchProfiles
import io.github.mircodhd.mvplayer.hud.DeviceSampler
import io.github.mircodhd.mvplayer.hud.HudDataHub
import io.github.mircodhd.mvplayer.settings.GameSettings
import io.github.mircodhd.mvplayer.settings.GlobalSettings
import io.github.mircodhd.mvplayer.settings.HudSettings
import io.github.mircodhd.mvplayer.settings.Renderer
import io.github.mircodhd.mvplayer.settings.SettingsStore
import io.github.mircodhd.mvplayer.storage.DocumentNode
import io.github.mircodhd.mvplayer.ui.game.EditorRequest
import io.github.mircodhd.mvplayer.ui.game.GameOverlay
import io.github.mircodhd.mvplayer.ui.game.GameOverlayActions
import io.github.mircodhd.mvplayer.ui.game.GameOverlayState
import io.github.mircodhd.mvplayer.ui.game.TextInputRequest
import io.github.mircodhd.mvplayer.ui.theme.MvPlayerTheme
import io.github.mircodhd.mvplayer.web.GameRequestHandler
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** Fullscreen WebView that runs one game, with a Compose overlay for controls and menus. */
class GameActivity : ComponentActivity(), GameOverlayActions, PlayerBridge.Host {
    companion object {
        const val EXTRA_GAME_ID = "game_id"
        private const val TAG = "Game"
        private const val LIFECYCLE = "Lifecycle"
    }

    private lateinit var container: FrameLayout
    private lateinit var loading: TextView
    private var webView: WebView? = null
    private var requestHandler: GameRequestHandler? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val createdAt = SystemClock.elapsedRealtime()
    private var gameId: String? = null
    /** Start of the current foreground stretch, for the library's play time. */
    private var resumedAt = 0L
    /** Foreground time of this session before the current stretch, for the HUD. */
    private var sessionPlayedMs = 0L
    private var appliedFrameRateCap = -1
    /** Renderer the running page was started with; changing it needs a restart. */
    private var runningRenderer: Renderer? = null
    private var promptedRenderer: Renderer? = null
    private var appliedHudInterval = 0
    private val deviceSamplerDelegate = lazy { DeviceSampler(this, displayHz = { display?.refreshRate }) }
    private val deviceSampler by deviceSamplerDelegate
    private val hudHub by lazy { HudDataHub(deviceSampler, sessionMs = ::sessionPlayTime) }

    /** Android-side half of the periodic performance line (the game logs its own half). */
    private val deviceLog = object : Runnable {
        override fun run() {
            val seconds = DebugLog.settings.statsIntervalSeconds
            if (seconds <= 0 || isDestroyed) return
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val d = deviceSampler.sample()
                DebugLog.i("Stats", "Device: battery ${d.batteryPercent ?: "?"} %" +
                    (if (d.charging == true) " charging" else "") +
                    ", ${d.batteryTempC ?: "?"} °C, thermal status ${d.thermalStatus ?: "?"}, " +
                    "headroom ${d.thermalHeadroom?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: "?"}, " +
                    "app memory ${d.appPssMb ?: "?"} MB, display ${d.displayHz?.let { "%.0f".format(java.util.Locale.ROOT, it) } ?: "?"} Hz")
            }
            container.postDelayed(this, seconds * 1000L)
        }
    }

    private val settingsStore by lazy { SettingsStore.get(this) }
    private val layoutRepository by lazy { LayoutRepository(this) }
    private lateinit var overlayState: GameOverlayState

    private val router = InputRouter(
        mapping = io.github.mircodhd.mvplayer.input.ControllerMapping.DEFAULT,
        onBits = ::sendPad,
        onAppAction = ::onAppAction,
    )
    private val gamepad = GamepadInput { controls -> router.setHardware(controls) }

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val inputManager by lazy { getSystemService(InputManager::class.java) }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            DebugLog.i(LIFECYCLE, "Input device added: ${describeDevice(deviceId)}")
            updateControllerConnected()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            DebugLog.d(LIFECYCLE, "Input device changed: ${describeDevice(deviceId)}")
            updateControllerConnected()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            DebugLog.i(LIFECYCLE, "Input device removed: $deviceId")
            gamepad.onDeviceRemoved(deviceId)
            updateControllerConnected()
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            addedDevices.filter { it.isSink }.forEach { DebugLog.i("AudioOut", "Output added: ${describeAudioDevice(it)}") }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            removedDevices.filter { it.isSink }.forEach { DebugLog.i("AudioOut", "Output removed: ${describeAudioDevice(it)}") }
        }
    }

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        DebugLog.w("Thermal", "Thermal status changed to ${thermalName(status)}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.i(LIFECYCLE, "onCreate (recreated=${savedInstanceState != null})")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val entry = intent.getStringExtra(EXTRA_GAME_ID)?.let(GameLibrary.get(this)::find)
        gameId = entry?.id
        overlayState = GameOverlayState(entry?.title.orEmpty())

        container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        loading = TextView(this).apply {
            setText(R.string.game_loading)
            setTextColor(Color.GRAY)
        }
        container.addView(loading, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        val overlayView = ComposeView(this).apply {
            setContent { MvPlayerTheme { GameOverlay(overlayState, this@GameActivity) } }
        }
        container.addView(overlayView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(container)

        onBackPressedDispatcher.addCallback(this) {
            DebugLog.i(TAG, "Back pressed, opening quick menu")
            overlayState.quickMenu = true
        }
        inputManager.registerInputDeviceListener(deviceListener, null)
        inputManager.inputDeviceIds
            .filter { InputDevice.getDevice(it)?.isVirtual == false }
            .forEach { DebugLog.i(LIFECYCLE, "Input device present: ${describeDevice(it)}") }
        updateControllerConnected()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null) // reports current outputs too
        powerManager.addThermalStatusListener(thermalListener)
        DebugLog.i("Thermal", "Thermal status ${thermalName(powerManager.currentThermalStatus)}")

        loadSettings()
        observeModalState()

        if (entry == null) {
            DebugLog.e(TAG, "Started without a known game")
            finish()
            return
        }
        DebugLog.i(TAG, "Opening game in ${entry.location} (library id $gameId)")
        executor.execute {
            val result = runCatching {
                GameInfoCache.get(entry.uri)?.let { (info, age) ->
                    DebugLog.i(TAG, "Using the library's scan of the game (${age / 1000} s old)")
                    return@runCatching info
                }
                val dir = DocumentNode.root(this, entry.treeUri) ?: error("No access to ${entry.location}")
                val webRoot = GameInspector.detect(dir).webRoot ?: error("No MV game in ${entry.location}")
                GameInspector.inspectMv(webRoot, dir.name).also { GameInfoCache.put(entry.uri, it) }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(::startGame) { showFatalError(it) }
            }
        }
    }

    // --- Settings -----------------------------------------------------------------------

    private fun loadSettings() {
        overlayState.global = settingsStore.global()
        overlayState.game = gameId?.let(settingsStore::game) ?: GameSettings()
        overlayState.layouts = layoutRepository.all()
        applySettings()
    }

    /** Applies the effective settings to input, overlay and frame rate, also mid-game. */
    private fun applySettings() {
        val effective = settingsStore.effective(gameId)
        router.mapping = effective.mapping
        overlayState.overlay = effective.overlay
        val layout = layoutRepository.find(effective.overlay.layoutId)
        overlayState.layout = if (effective.overlay.mirror) layout.mirrored() else layout
        if (effective.frameRateCap != appliedFrameRateCap) {
            appliedFrameRateCap = effective.frameRateCap
            applyFrameRateCap(effective.frameRateCap)
            webView?.evaluateJavascript("window.MvPlayer&&MvPlayer.setFrameRateCap&&MvPlayer.setFrameRateCap(${effective.frameRateCap})", null)
        }
        applyHud(overlayState.global.hud)
        val running = runningRenderer
        if (running != null && effective.renderer != running && effective.renderer != promptedRenderer) {
            promptedRenderer = effective.renderer
            DebugLog.i(TAG, "Renderer changed to ${effective.renderer} while running ${running}; offering a restart")
            overlayState.restartPrompt = true
        }
        DebugLog.d(TAG, "Effective settings: $effective")
    }

    /** Starts or stops the stats reporting in the page to match the HUD settings. */
    private fun applyHud(hud: HudSettings) {
        val interval = if (hud.enabled) hud.intervalMs else 0
        if (interval == appliedHudInterval) return
        appliedHudInterval = interval
        if (interval == 0) {
            overlayState.hud = null
            hudHub.reset()
        }
        DebugLog.i("Hud", if (interval > 0) "Performance HUD on, every $interval ms" else "Performance HUD off")
        webView?.evaluateJavascript("window.MvPlayer&&MvPlayer.perf&&MvPlayer.perf.setReporting($interval)", null)
    }

    override fun onStats(json: String) {
        runOnUiThread {
            if (appliedHudInterval > 0 && !isDestroyed) overlayState.hud = hudHub.onJsStats(json)
        }
    }

    override fun onHudDrawn(nanos: Long) = hudHub.reportDrawNanos(nanos)

    private fun sessionPlayTime(): Long =
        sessionPlayedMs + if (resumedAt > 0) SystemClock.elapsedRealtime() - resumedAt else 0

    override fun onGlobalSettingsChange(settings: GlobalSettings) {
        settingsStore.setGlobal(settings)
        overlayState.global = settings
        applySettings()
    }

    override fun onRestartGame() {
        DebugLog.i(TAG, "Restarting the game to switch the renderer")
        overlayState.restartPrompt = false
        recreate()
    }

    override fun onGameSettingsChange(settings: GameSettings) {
        val id = gameId ?: return
        settingsStore.setGame(id, settings)
        overlayState.game = settings
        applySettings()
    }

    // --- Game -----------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun startGame(info: GameInfo) {
        val (profileIds, patches) = PatchProfiles.select(info)
        DebugLog.i(TAG, "Patch profiles: $profileIds")
        val debugSettings = DebugLog.settings
        val effective = settingsStore.effective(gameId)
        runningRenderer = effective.renderer
        promptedRenderer = effective.renderer
        appliedHudInterval = if (effective.hud.enabled) effective.hud.intervalMs else 0
        val config = JSONObject()
            .put("title", info.title)
            .put("engineVersion", info.engineVersion ?: JSONObject.NULL)
            .put("audioExt", info.audioExt)
            .put("videoExt", info.videoExt)
            .put("profiles", JSONArray(profileIds))
            .put("frameRateCap", appliedFrameRateCap.coerceAtLeast(0))
            .put("renderer", effective.renderer.id)
            .put("hud", JSONObject().put("intervalMs", appliedHudInterval))
            .put("debug", JSONObject()
                .put("verbose", debugSettings.verbose)
                .put("statsIntervalSeconds", debugSettings.statsIntervalSeconds))
        val handler = GameRequestHandler(assets, info, patches, config.toString())
        requestHandler = handler
        val bridge = PlayerBridge(info.webRoot, this)

        WebView.setWebContentsDebuggingEnabled(debugSettings.remoteDebugging)
        val view = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
                safeBrowsingEnabled = false
            }
            addJavascriptInterface(bridge, PlayerBridge.JS_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                    handler.handle(request)

                // Games must not navigate the WebView away from themselves.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val block = request.url.host != GameRequestHandler.HOST
                    if (block) DebugLog.w(TAG, "Blocked navigation to ${request.url}")
                    return block
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    DebugLog.i(TAG, "Page started: $url")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    DebugLog.i(TAG, "Page finished: $url, ${SystemClock.elapsedRealtime() - createdAt} ms after onCreate")
                    container.removeView(loading)
                    overlayState.gameReady = true
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    DebugLog.w(TAG, "WebView error ${error.errorCode} '${error.description}' for ${request.url} " +
                        "(main frame=${request.isForMainFrame})")
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    DebugLog.e(TAG, "WebView renderer gone (crash=${detail.didCrash()}, " +
                        "priority at exit=${detail.rendererPriorityAtExit()})")
                    DebugLog.flush()
                    container.removeView(view)
                    view.destroy()
                    webView = null
                    finish()
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val level = when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> DebugLog.Level.ERROR
                        ConsoleMessage.MessageLevel.WARNING -> DebugLog.Level.WARN
                        ConsoleMessage.MessageLevel.DEBUG, ConsoleMessage.MessageLevel.TIP -> DebugLog.Level.DEBUG
                        else -> DebugLog.Level.INFO
                    }
                    val source = message.sourceId().substringAfter(GameRequestHandler.HOST)
                    DebugLog.log(level, "Console", "${message.message()} ($source:${message.lineNumber()})")
                    return true
                }
            }
        }
        container.addView(view, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        view.requestFocus()
        webView = view
        // pauseTimers() is process-wide and may still be in effect from a previous game in
        // this process; onResume ran before this view existed, so resume here.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resumeTimers()
        logMediaVolume()
        view.loadUrl(GameRequestHandler.START_URL)
        DebugLog.settings.statsIntervalSeconds.takeIf { it > 0 }?.let { container.postDelayed(deviceLog, it * 1000L) }
    }

    // --- Input ------------------------------------------------------------------------------

    private fun sendPad(bits: Int) {
        webView?.evaluateJavascript("window.MvPlayer&&MvPlayer.setPad($bits)", null)
    }

    private fun onAppAction(action: InputAction) {
        if (overlayState.isModal) return
        when (action) {
            InputAction.QUICK_MENU -> {
                DebugLog.i(TAG, "Quick menu opened by controller")
                overlayState.quickMenu = true
            }
            InputAction.TOGGLE_HUD -> {
                val global = overlayState.global
                onGlobalSettingsChange(global.copy(hud = global.hud.copy(enabled = !global.hud.enabled)))
            }
            else -> Unit
        }
    }

    override fun onGamepadActions(actions: Set<InputAction>) = router.setOverlay(actions)

    private fun updateControllerConnected() {
        val connected = inputManager.inputDeviceIds.any { GamepadInput.isController(InputDevice.getDevice(it)) }
        if (overlayState.controllerConnected != connected) DebugLog.i(TAG, "Controller connected: $connected")
        overlayState.controllerConnected = connected
    }

    // Lint flags androidx's restricted override of this framework method; overriding it is fine.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // While a menu covers the game, Compose handles the keys (focus navigation, back).
        if (!overlayState.isModal && gamepad.handleKeyEvent(event)) return true
        if (event.repeatCount == 0 && DebugLog.verbose) {
            DebugLog.v("Input", "Key ${KeyEvent.keyCodeToString(event.keyCode)} " +
                "${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} from '${event.device?.name}' " +
                "sources=0x${event.source.toString(16)} (passed on)")
        }
        if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            container.post { logMediaVolume() }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        (!overlayState.isModal && gamepad.handleMotionEvent(event)) || super.dispatchGenericMotionEvent(event)

    /** Pauses the game while a menu covers it and releases all held input. */
    private fun observeModalState() {
        lifecycleScope.launch {
            // drop(1): the first value is the initial state, not a change.
            snapshotFlow { overlayState.isModal }.drop(1).collect { modal ->
                DebugLog.i(TAG, if (modal) "Menu open, game paused" else "Menu closed, game resumed")
                gamepad.reset()
                router.reset()
                val call = if (modal) "pause" else "resume"
                webView?.evaluateJavascript("window.MvPlayer&&MvPlayer.$call&&MvPlayer.$call('menu')", null)
                if (!modal) {
                    hideSystemBars()
                    webView?.requestFocus()
                }
            }
        }
    }

    // --- Overlay callbacks ----------------------------------------------------------------

    override fun requestTextInput(current: String, maxLength: Int) {
        runOnUiThread { overlayState.textInput = TextInputRequest(current, maxLength) }
    }

    override fun onTextResult(text: String?) {
        DebugLog.i("TextInput", if (text == null) "Text input cancelled" else "Text input: ${text.length} characters")
        val argument = text?.let { JSONObject.quote(it) } ?: "null"
        webView?.evaluateJavascript("window.MvPlayer&&MvPlayer.onTextInput&&MvPlayer.onTextInput($argument)", null)
        hideSystemBars()
    }

    override fun exitGame() {
        runOnUiThread { finish() }
    }

    override fun onQuit() {
        DebugLog.i(TAG, "Quit confirmed")
        finish()
    }

    override fun onCustomizeLayout(layout: GamepadLayout) {
        // Edit what the player sees: mirrored if left-handed mode is on (saved as shown).
        val mirror = overlayState.overlay.mirror
        val preset = layoutRepository.presets().firstOrNull { it.id == (layout.basedOn ?: layout.id) } ?: layout
        val shown = if (mirror) layout.mirrored() else layout
        val original = if (mirror) preset.mirrored() else preset
        val state = overlayState
        state.quickMenu = false
        state.hideControls = true
        // Two frames later the menu and controls are gone; take the screenshot then.
        container.postOnAnimation {
            container.postOnAnimation {
                captureWindow { bitmap ->
                    state.hideControls = false
                    state.editor = EditorRequest(shown, original, bitmap?.asImageBitmap())
                }
            }
        }
    }

    override fun onEditorSave(layout: GamepadLayout, forGame: Boolean, asCopy: Boolean) {
        val base = layoutRepository.all().firstOrNull { it.id == layout.id } ?: layout
        val saved = layoutRepository.saveCustom(layout, LayoutNames.customName(this, base), asCopy)
        val id = gameId
        if (forGame && id != null) {
            // Saved as shown, so it must not be mirrored again; null when that is the default.
            val mirror = if (settingsStore.global().overlay.mirror) false else null
            onGameSettingsChange(settingsStore.game(id).copy(overlayLayoutId = saved.id, overlayMirror = mirror))
        } else {
            val global = settingsStore.global()
            settingsStore.setGlobal(global.copy(overlay = global.overlay.copy(layoutId = saved.id, mirror = false)))
        }
        loadSettings()
    }

    private fun captureWindow(onResult: (Bitmap?) -> Unit) {
        val decor = window.decorView
        if (decor.width <= 0 || decor.height <= 0) return onResult(null)
        val bitmap = createBitmap(decor.width, decor.height)
        PixelCopy.request(window, bitmap, { result ->
            if (result != PixelCopy.SUCCESS) DebugLog.w(TAG, "Screenshot for the layout editor failed ($result)")
            onResult(if (result == PixelCopy.SUCCESS) bitmap else null)
        }, Handler(Looper.getMainLooper()))
    }

    // --- Lifecycle --------------------------------------------------------------------------

    override fun onPause() {
        super.onPause()
        DebugLog.i(LIFECYCLE, "onPause")
        recordPlayTime()
        requestHandler?.let { DebugLog.i("Request", "Totals: ${it.stats()}") }
        gamepad.reset()
        router.reset()
        webView?.run {
            evaluateJavascript("window.MvPlayer&&MvPlayer.onPause()", null)
            onPause()
            pauseTimers()
        }
        DebugLog.flush()
    }

    override fun onResume() {
        super.onResume()
        DebugLog.i(LIFECYCLE, "onResume")
        resumedAt = SystemClock.elapsedRealtime()
        hideSystemBars()
        // Settings may have changed in the library while the game was in the background.
        if (::overlayState.isInitialized) loadSettings()
        webView?.run {
            resumeTimers()
            onResume()
            evaluateJavascript("window.MvPlayer&&MvPlayer.onResume()", null)
        }
        if (webView != null) logMediaVolume()
    }

    override fun onStart() {
        super.onStart()
        DebugLog.i(LIFECYCLE, "onStart")
    }

    override fun onStop() {
        DebugLog.i(LIFECYCLE, "onStop")
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DebugLog.i(LIFECYCLE, "onConfigurationChanged: $newConfig")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        DebugLog.i(LIFECYCLE, "Window focus $hasFocus")
        if (hasFocus) {
            hideSystemBars()
        } else {
            gamepad.reset()
            router.reset()
        }
    }

    override fun onDestroy() {
        DebugLog.i(LIFECYCLE, "onDestroy (finishing=$isFinishing)")
        inputManager.unregisterInputDeviceListener(deviceListener)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        powerManager.removeThermalStatusListener(thermalListener)
        executor.shutdownNow()
        container.removeCallbacks(deviceLog)
        if (deviceSamplerDelegate.isInitialized()) deviceSampler.close()
        webView?.run {
            // Undo the process-wide pauseTimers() from onPause, or the next game started in
            // this process hangs while loading.
            resumeTimers()
            container.removeView(this)
            destroy()
        }
        webView = null
        DebugLog.flush()
        super.onDestroy()
    }

    private fun showFatalError(error: Throwable) {
        DebugLog.e(TAG, "Could not start game", error)
        AlertDialog.Builder(this)
            .setMessage(error.message ?: error.toString())
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { finish() }
            .show()
    }

    /**
     * Asks Android to drive the display at the cap (e.g. 60 instead of 120 Hz), or lifts the
     * preference for 0. This saves power in the panel too; framerate.js covers displays
     * that ignore the request.
     */
    private fun applyFrameRateCap(cap: Int) {
        window.attributes = window.attributes.apply { preferredRefreshRate = cap.coerceAtLeast(0).toFloat() }
        container.postDelayed({
            DebugLog.i("Frames", "Requested ${if (cap > 0) "$cap Hz" else "no limit"}, " +
                "display runs at ${"%.1f".format(display?.refreshRate ?: 0f)} Hz")
        }, 1000)
    }

    private fun recordPlayTime() {
        val id = gameId ?: return
        if (resumedAt == 0L) return
        val played = SystemClock.elapsedRealtime() - resumedAt
        resumedAt = 0L
        sessionPlayedMs += played
        GameLibrary.get(this).recordPlay(id, played)
        DebugLog.d(TAG, "Recorded ${played / 1000} s of play time")
    }

    private fun logMediaVolume() {
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        DebugLog.i("AudioOut", "Media volume $volume/$max, mode ${audioManager.mode}, " +
            "music active=${audioManager.isMusicActive}")
    }

    private fun describeDevice(deviceId: Int): String {
        val device = InputDevice.getDevice(deviceId) ?: return "$deviceId (gone)"
        return "$deviceId '${device.name}' vendor=0x${device.vendorId.toString(16)} " +
            "product=0x${device.productId.toString(16)} sources=0x${device.sources.toString(16)} " +
            "external=${device.isExternal}"
    }

    private fun describeAudioDevice(device: AudioDeviceInfo): String {
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth SCO"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE speaker"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "remote submix"
            else -> "type ${device.type}"
        }
        return "$type '${device.productName}' (id ${device.id})"
    }

    private fun thermalName(status: Int) = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> status.toString()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
