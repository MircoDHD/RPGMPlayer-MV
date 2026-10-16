package io.github.mircodhd.mvplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.debug.LogExporter
import io.github.mircodhd.mvplayer.game.Engine
import io.github.mircodhd.mvplayer.game.GameInspector
import java.io.File
import java.util.concurrent.Executors

/** Start screen: obtains file access and lets the user pick a game folder. */
class MainActivity : ComponentActivity() {
    private lateinit var message: TextView
    private lateinit var grantAccess: Button
    private lateinit var chooseFolder: Button
    private lateinit var continueGame: Button
    private lateinit var shareLogs: Button
    private lateinit var logLocation: TextView
    private val executor = Executors.newSingleThreadExecutor()

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val dir = StoragePaths.treeUriToFile(this, uri)
        DebugLog.i(TAG, "Picked $uri -> $dir")
        if (dir == null || !dir.isDirectory) showMessage(getString(R.string.main_folder_unresolvable)) else openGame(dir)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        message = findViewById(R.id.message)
        grantAccess = findViewById(R.id.grantAccess)
        chooseFolder = findViewById(R.id.chooseFolder)
        continueGame = findViewById(R.id.continueGame)
        shareLogs = findViewById(R.id.shareLogs)
        logLocation = findViewById(R.id.logLocation)

        grantAccess.setOnClickListener { requestAllFilesAccess() }
        chooseFolder.setOnClickListener { pickFolder.launch(null) }
        continueGame.setOnClickListener { lastGame(this)?.let(::openGame) }
        shareLogs.setOnClickListener { exportLogs() }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        val hasAccess = Environment.isExternalStorageManager()
        DebugLog.d(TAG, "onResume, all-files access=$hasAccess")
        if (hasAccess) DebugLog.relocateIfPossible()
        grantAccess.visibility = if (hasAccess) View.GONE else View.VISIBLE
        chooseFolder.isEnabled = hasAccess
        if (!hasAccess) showMessage(getString(R.string.main_access_needed)) else hideMessage()

        val last = lastGame(this)
        continueGame.visibility = if (hasAccess && last != null) View.VISIBLE else View.GONE
        if (last != null) continueGame.text = getString(R.string.main_continue, lastTitle(this) ?: last.name)

        val logDir = DebugLog.logDirectory()
        logLocation.text = logDir?.let { getString(R.string.main_log_location, displayPath(it)) }.orEmpty()
    }

    private fun exportLogs() {
        shareLogs.isEnabled = false
        val title = getString(R.string.main_share_logs_chooser)
        executor.execute {
            val intent = runCatching { LogExporter.createShareIntent(this, title) }
                .onFailure { DebugLog.e(TAG, "Log export failed", it) }
                .getOrNull()
            runOnUiThread {
                shareLogs.isEnabled = true
                if (intent != null) startActivity(intent) else showMessage(getString(R.string.main_no_logs))
            }
        }
    }

    /** "/storage/emulated/0/Documents/..." -> "Documents/..." */
    private fun displayPath(dir: File): String =
        dir.absolutePath.removePrefix(Environment.getExternalStorageDirectory().absolutePath).trimStart('/')

    private fun openGame(dir: File) {
        val match = GameInspector.detect(dir)
        val error = when (match.engine) {
            Engine.MV -> null
            Engine.MZ -> R.string.engine_mz_unsupported
            Engine.RGSS -> R.string.engine_rgss_unsupported
            Engine.RPG2K -> R.string.engine_2k_unsupported
            Engine.UNKNOWN -> R.string.engine_unknown
        }
        if (error != null) {
            DebugLog.w(TAG, "Not starting ${match.engine} game in $dir")
            showMessage(getString(error))
            return
        }
        prefs(this).edit {
            putString(KEY_LAST_GAME, dir.absolutePath)
            remove(KEY_LAST_TITLE) // GameActivity stores the real title once it has read it
        }
        startActivity(Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_GAME_DIR, dir.absolutePath))
    }

    private fun requestAllFilesAccess() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri())
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    private fun hideMessage() {
        message.visibility = View.GONE
    }

    companion object {
        private const val TAG = "Main"
        private const val KEY_LAST_GAME = "last_game_dir"
        private const val KEY_LAST_TITLE = "last_game_title"

        private fun prefs(context: Context) = context.getSharedPreferences("player", MODE_PRIVATE)

        fun lastGame(context: Context): File? =
            prefs(context).getString(KEY_LAST_GAME, null)?.let(::File)?.takeIf { it.isDirectory }

        private fun lastTitle(context: Context): String? = prefs(context).getString(KEY_LAST_TITLE, null)

        fun rememberTitle(context: Context, title: String) = prefs(context).edit { putString(KEY_LAST_TITLE, title) }
    }
}
