package dev.soto.panelgrove

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.soto.panelgrove.core.*
import dev.soto.panelgrove.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class CustomCoverUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as PanelgroveApplication

    @Test fun pickerCancellationSelectionAndRemovalRefreshTheVisibleCover() {
        val file = File(app.sources.managedDirectory, "cover-ui-${UUID.randomUUID()}.cbz")
        InstrumentationRegistry.getInstrumentation().context.assets.open("fixtures/sample.cbz").use { input -> file.outputStream().use { input.copyTo(it) } }
        val book = runBlocking { app.library.importFile(Uri.parse("panelgrove://managed/${file.name}")) }
        val id = "hardening/cover-ui-${UUID.randomUUID()}.png"
        val bytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA); compress(Bitmap.CompressFormat.PNG, 100, output); recycle() }
            output.toByteArray()
        }
        app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
            Bundle().apply { putString("id", id); putByteArray("bytes", bytes) })
        var chosen: Uri? = null
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                assertEquals(listOf("image/*"), (input as Array<*>).toList())
                dispatchResult(requestCode, if (chosen == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, chosen?.let { Intent().setData(it) })
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        try {
            compose.activity.setContent {
                val books by app.library.books.collectAsState(initial = emptyList())
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    PanelgroveTheme(AppTheme.DARK) {
                        books.find { it.id == book.id }?.let {
                            BookDetails(it, ViewModelProvider(compose.activity)[LibraryViewModel::class.java], {}, {})
                        }
                    }
                }
            }
            compose.onNodeWithText("Choose custom cover").performClick()
            compose.onNodeWithText("Remove custom cover").assertDoesNotExist()
            assertNull(runBlocking { app.library.book(book.id)!!.customCover })
            chosen = DocumentsContract.buildDocumentUri("dev.soto.panelgrove.test.documents", id)
            compose.onNodeWithText("Choose custom cover").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.customCover != null } }
            compose.waitUntil(10_000) {
                val pixels = compose.onNodeWithContentDescription("Cover of ${book.title}").capturePixels()
                pixels[pixels.width / 2, pixels.height / 2] == Color.Magenta
            }
            val directory = File(app.filesDir, "display-validation").apply { mkdirs() }
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { image ->
                File(directory, "custom-cover.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            }
            compose.onNodeWithText("Remove custom cover").performClick()
            compose.waitUntil(10_000) { runBlocking { app.library.book(book.id)?.customCover == null } }
            compose.onNodeWithText("Remove custom cover").assertDoesNotExist()
        } finally {
            runBlocking { app.library.remove(book.id, false) }; file.delete()
            app.contentResolver.call(Uri.parse("content://dev.soto.panelgrove.test.documents"), "fixture", null,
                Bundle().apply { putString("id", id); putBoolean("reset", true) })
        }
    }
}
