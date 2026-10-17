// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mircodhd.mvplayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.input.InputManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.mircodhd.mvplayer.bridge.PlayerBridge
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.game.GameInfo
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.input.GamepadInput
import io.github.mircodhd.mvplayer.library.GameLibrary
import io.github.mircodhd.mvplayer.patch.PatchProfiles
import io.github.mircodhd.mvplayer.web.GameRequestHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Fullscreen WebView that runs one game. */
class GameActivity : ComponentActivity() {
    companion object {
        const val EXTRA_GAME_DIR = "game_dir"
        private const val TAG = "Game"
        private const val LIFECYCLE = "Lifecycle"
        private const val SELECT_START = (1 shl 8) or (1 shl 9)
    }

    private lateinit var container: FrameLayout
    private lateinit var loading: TextView
    private var webView: WebView? = null
    private var requestHandler: GameRequestHandler? = null
    private var quitDialog: AlertDialog? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val gamepad = GamepadInput(::onGamepadChanged)
    private val createdAt = SystemClock.elapsedRealtime()
    private var gameDir: File? = null
    /** Start of the current foreground stretch, for the library's play time. */
    private var resumedAt = 0L

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            DebugLog.i(LIFECYCLE, "Input device added: ${describeDevice(deviceId)}")
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            DebugLog.d(LIFECYCLE, "Input device changed: ${describeDevice(deviceId)}")
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            DebugLog.i(LIFECYCLE, "Input device removed: $deviceId")
            gamepad.onDeviceRemoved(deviceId)
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

        container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        loading = TextView(this).apply {
            setText(R.string.game_loading)
            setTextColor(Color.GRAY)
        }
        container.addView(loading, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
        setContentView(container)

        onBackPressedDispatcher.addCallback(this) {
            DebugLog.i(TAG, "Back pressed")
            confirmQuit()
        }
        val inputManager = getSystemService(InputManager::class.java)
        inputManager.registerInputDeviceListener(deviceListener, null)
        inputManager.inputDeviceIds
            .filter { InputDevice.getDevice(it)?.isVirtual == false }
            .forEach { DebugLog.i(LIFECYCLE, "Input device present: ${describeDevice(it)}") }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null) // reports current outputs too
        powerManager.addThermalStatusListener(thermalListener)
        DebugLog.i("Thermal", "Thermal status ${thermalName(powerManager.currentThermalStatus)}")

        val dir = intent.getStringExtra(EXTRA_GAME_DIR)?.let(::File)
        if (dir == null) {
            DebugLog.e(TAG, "Started without a game folder")
            finish()
            return
        }
        gameDir = dir
        DebugLog.i(TAG, "Opening game in $dir")
        executor.execute {
            val result = runCatching {
                val webRoot = GameInspector.detect(dir).webRoot ?: error("No MV game in $dir")
                GameInspector.inspectMv(webRoot)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.fold(::startGame) { showFatalError(it) }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startGame(info: GameInfo) {
        val (profileIds, patches) = PatchProfiles.select(info)
        DebugLog.i(TAG, "Patch profiles: $profileIds")
        val debugSettings = DebugLog.settings
        val playerSettings = PlayerSettings.load(this)
        DebugLog.i(TAG, "Player settings: $playerSettings")
        applyFrameRateCap(playerSettings.frameRateCap)
        val config = JSONObject()
            .put("title", info.title)
            .put("engineVersion", info.engineVersion ?: JSONObject.NULL)
            .put("audioExt", info.audioExt)
            .put("videoExt", info.videoExt)
            .put("profiles", JSONArray(profileIds))
            .put("frameRateCap", playerSettings.frameRateCap)
            .put("debug", JSONObject()
                .put("verbose", debugSettings.verbose)
                .put("statsIntervalSeconds", debugSettings.statsIntervalSeconds))
        val handler = GameRequestHandler(assets, info, patches, config.toString())
        requestHandler = handler
        val bridge = PlayerBridge(File(info.webRoot, "save")) { runOnUiThread { finish() } }

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

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    DebugLog.i(TAG, "Page started: $url")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    DebugLog.i(TAG, "Page finished: $url, ${SystemClock.elapsedRealtime() - createdAt} ms after onCreate")
                    container.removeView(loading)
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
        logMediaVolume()
        view.loadUrl(GameRequestHandler.START_URL)
    }

    private fun onGamepadChanged(buttons: Int) {
        if (buttons and SELECT_START == SELECT_START) {
            DebugLog.i(TAG, "Select+Start pressed, asking to quit")
            confirmQuit()
            return
        }
        webView?.evaluateJavascript("window.MvPlayer&&MvPlayer.setPad($buttons)", null)
    }

    // Lint flags androidx's restricted override of this framework method; overriding it is fine.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepad.handleKeyEvent(event)) return true
        if (event.repeatCount == 0 && DebugLog.verbose) {
            DebugLog.v("Input", "Key ${KeyEvent.keyCodeToString(event.keyCode)} " +
                "${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} from '${event.device?.name}' " +
                "sources=0x${event.source.toString(16)} (passed to WebView)")
        }
        if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            container.post { logMediaVolume() }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        gamepad.handleMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    override fun onPause() {
        super.onPause()
        DebugLog.i(LIFECYCLE, "onPause")
        recordPlayTime()
        requestHandler?.let { DebugLog.i("Request", "Totals: ${it.stats()}") }
        gamepad.reset()
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
        if (hasFocus) hideSystemBars() else gamepad.reset()
    }

    override fun onDestroy() {
        DebugLog.i(LIFECYCLE, "onDestroy (finishing=$isFinishing)")
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(deviceListener)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        powerManager.removeThermalStatusListener(thermalListener)
        executor.shutdownNow()
        quitDialog?.dismiss()
        webView?.run {
            container.removeView(this)
            destroy()
        }
        webView = null
        DebugLog.flush()
        super.onDestroy()
    }

    private fun confirmQuit() {
        if (quitDialog?.isShowing == true) return
        quitDialog = AlertDialog.Builder(this)
            .setTitle(R.string.game_quit_title)
            .setMessage(R.string.game_quit_message)
            .setPositiveButton(R.string.game_quit_confirm) { _, _ ->
                DebugLog.i(TAG, "Quit confirmed")
                finish()
            }
            .setNegativeButton(R.string.game_quit_cancel, null)
            .setOnDismissListener { hideSystemBars() }
            .show()
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
     * Asks Android to drive the display at the cap (e.g. 60 instead of 120 Hz). This saves
     * power in the panel too; framerate.js covers displays that ignore the request.
     */
    private fun applyFrameRateCap(cap: Int) {
        if (cap <= 0) return
        window.attributes = window.attributes.apply { preferredRefreshRate = cap.toFloat() }
        container.postDelayed({
            DebugLog.i("Frames", "Requested $cap Hz, display runs at ${"%.1f".format(display?.refreshRate ?: 0f)} Hz")
        }, 1000)
    }

    private fun recordPlayTime() {
        val dir = gameDir ?: return
        if (resumedAt == 0L) return
        val played = SystemClock.elapsedRealtime() - resumedAt
        resumedAt = 0L
        GameLibrary.get(this).recordPlay(dir.absolutePath, played)
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
