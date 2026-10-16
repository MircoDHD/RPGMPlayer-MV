package io.github.mircodhd.mvplayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
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
import io.github.mircodhd.mvplayer.game.GameInfo
import io.github.mircodhd.mvplayer.game.GameInspector
import io.github.mircodhd.mvplayer.input.GamepadInput
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
        private const val TAG = "GameActivity"
        private const val SELECT_START = (1 shl 8) or (1 shl 9)
    }

    private lateinit var container: FrameLayout
    private lateinit var loading: TextView
    private var webView: WebView? = null
    private var quitDialog: AlertDialog? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val gamepad = GamepadInput(::onGamepadChanged)

    private val isDebuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceChanged(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) = gamepad.onDeviceRemoved(deviceId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        onBackPressedDispatcher.addCallback(this) { confirmQuit() }
        getSystemService(InputManager::class.java).registerInputDeviceListener(deviceListener, null)

        val dir = intent.getStringExtra(EXTRA_GAME_DIR)?.let(::File)
        if (dir == null) {
            finish()
            return
        }
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
        MainActivity.rememberTitle(this, info.title)
        val (profileIds, patches) = PatchProfiles.select(info)
        Log.i(TAG, "Patch profiles: $profileIds")
        val config = JSONObject()
            .put("title", info.title)
            .put("engineVersion", info.engineVersion ?: JSONObject.NULL)
            .put("audioExt", info.audioExt)
            .put("videoExt", info.videoExt)
            .put("profiles", JSONArray(profileIds))
            .put("debug", isDebuggable)
        val handler = GameRequestHandler(assets, info, patches, config.toString())
        val bridge = PlayerBridge(File(info.webRoot, "save")) { runOnUiThread { finish() } }

        WebView.setWebContentsDebuggingEnabled(isDebuggable)
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
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    request.url.host != GameRequestHandler.HOST

                override fun onPageFinished(view: WebView, url: String) {
                    container.removeView(loading)
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.e(TAG, "WebView renderer gone (crash=${detail.didCrash()})")
                    container.removeView(view)
                    view.destroy()
                    webView = null
                    finish()
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val text = "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                    when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e("GameConsole", text)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("GameConsole", text)
                        else -> Log.i("GameConsole", text)
                    }
                    return true
                }
            }
        }
        container.addView(view, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        view.requestFocus()
        webView = view
        view.loadUrl(GameRequestHandler.START_URL)
    }

    private fun onGamepadChanged(buttons: Int) {
        if (buttons and SELECT_START == SELECT_START) {
            confirmQuit()
            return
        }
        webView?.evaluateJavascript("window.MvPlayer&&MvPlayer.setPad($buttons)", null)
    }

    // Lint flags androidx's restricted override of this framework method; overriding it is fine.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        gamepad.handleKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        gamepad.handleMotionEvent(event) || super.dispatchGenericMotionEvent(event)

    override fun onPause() {
        super.onPause()
        gamepad.reset()
        webView?.run {
            evaluateJavascript("window.MvPlayer&&MvPlayer.onPause()", null)
            onPause()
            pauseTimers()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        webView?.run {
            resumeTimers()
            onResume()
            evaluateJavascript("window.MvPlayer&&MvPlayer.onResume()", null)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else gamepad.reset()
    }

    override fun onDestroy() {
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(deviceListener)
        executor.shutdownNow()
        quitDialog?.dismiss()
        webView?.run {
            container.removeView(this)
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun confirmQuit() {
        if (quitDialog?.isShowing == true) return
        quitDialog = AlertDialog.Builder(this)
            .setTitle(R.string.game_quit_title)
            .setMessage(R.string.game_quit_message)
            .setPositiveButton(R.string.game_quit_confirm) { _, _ -> finish() }
            .setNegativeButton(R.string.game_quit_cancel, null)
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    private fun showFatalError(error: Throwable) {
        Log.e(TAG, "Could not start game", error)
        AlertDialog.Builder(this)
            .setMessage(error.message ?: error.toString())
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener { finish() }
            .show()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
