package io.github.mircodhd.mvplayer

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.mircodhd.mvplayer.debug.DebugLog
import io.github.mircodhd.mvplayer.debug.DeviceInfo

class PlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)
        DeviceInfo.log(this)

        // Get the stack trace of a crash into the session log before the process dies.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            DebugLog.e("Crash", "Uncaught exception on thread ${thread.name}", error)
            DebugLog.flush()
            previous?.uncaughtException(thread, error)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val name = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            else -> level.toString()
        }
        DebugLog.w("Memory", "onTrimMemory $name")
    }
}
