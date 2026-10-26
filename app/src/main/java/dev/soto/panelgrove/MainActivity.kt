package dev.soto.panelgrove

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.soto.panelgrove.ui.LibraryViewModel
import dev.soto.panelgrove.ui.PanelgroveApp

class MainActivity : ComponentActivity() {
    private val model: LibraryViewModel by viewModels()
    var readerKey: ((Boolean) -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PanelgroveApp(model) }
        if (savedInstanceState == null) receive(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    override fun onResume() { super.onResume(); model.refresh() }
    @Suppress("DEPRECATION")
    private fun receive(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }
        uris.take(50).filter { it.scheme == "content" }.forEachIndexed { i, uri ->
            model.import(uri, openAfter = i == 0)
        }
    }
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val next = when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> true
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> false
            else -> return super.onKeyDown(keyCode, event)
        }
        val handler = readerKey ?: return super.onKeyDown(keyCode, event)
        handler(next)
        return true
    }
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (readerKey != null && keyCode in listOf(android.view.KeyEvent.KEYCODE_VOLUME_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }
}
