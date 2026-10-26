package dev.soto.panelgrove

import android.util.Log

/** Technical details stay in Logcat; development builds also show them in the UI. */
object FailureMessages {
    fun user(error: Throwable, fallback: String): String {
        Log.w("Panelgrove", fallback, error)
        return if (BuildConfig.DEBUG) "$fallback\n${error.message ?: error.javaClass.simpleName}" else fallback
    }
    fun recoverable(error: Throwable, operation: String) { Log.w("Panelgrove", operation, error) }
}
