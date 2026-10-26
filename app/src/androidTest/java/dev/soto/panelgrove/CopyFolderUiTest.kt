package dev.soto.panelgrove

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.soto.panelgrove.ui.LibraryViewModel
import dev.soto.panelgrove.core.AppPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import android.provider.DocumentsContract
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CopyFolderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun enablingCopyingWithoutAFolderPromptsAndCancelKeepsItOff() {
        val app = compose.activity.application as PanelgroveApplication
        val previous = runBlocking { app.preferences.current() }
        try {
            runBlocking { app.preferences.update { it.copy(copyImports = false, copyFolderUri = null, copyFolderName = null) } }
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithContentDescription("Copy imported files").performScrollTo().assertIsOff().performClick()
            compose.onNodeWithText("Choose an import folder").assertIsDisplayed()
            assertFalse(runBlocking { app.preferences.current().copyImports })
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithContentDescription("Copy imported files").assertIsOff()
            compose.onNodeWithText("No import folder selected").assertIsDisplayed()
            compose.onNodeWithContentDescription("Choose import folder").performClick()
            compose.onNodeWithText("Choose an import folder").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
        } finally {
            compose.runOnIdle { ViewModelProvider(compose.activity)[LibraryViewModel::class.java].selectCopyFolder(null) }
            runBlocking { app.preferences.set(previous) }
        }
    }
    @Test fun restoredImportFolderRequestsPermissionAndCanBeDeferredAndRetried() {
        val app = compose.activity.application as PanelgroveApplication
        val previous = runBlocking { app.preferences.current() }
        val pending = runBlocking { app.preferences.pendingImportFolderRestore.first() }
        val model = ViewModelProvider(compose.activity)[LibraryViewModel::class.java]
        try {
            val folder = DocumentsContract.buildTreeDocumentUri("dev.soto.panelgrove.test.documents", "hardening/previous")
            runBlocking { app.preferences.setRestored(AppPreferences(copyImports = false, copyFolderUri = folder.toString(), copyFolderName = "Saved manga")) }
            compose.onNodeWithText("Restore import folder").assertIsDisplayed()
            compose.onNodeWithText("Choose folder").assertIsDisplayed()
            compose.onNodeWithText("Later").performClick()
            compose.onNodeWithText("Restore import folder").assertDoesNotExist()
            assertEquals(folder.toString(), runBlocking { app.preferences.pendingImportFolderRestore.first() })
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Find existing books").performScrollTo().performClick()
            compose.onNodeWithText("Restore import folder").assertIsDisplayed()
            compose.onNodeWithText("Later").performClick()
        } finally {
            compose.runOnIdle { model.selectRestoreFolder(null) }
            runBlocking {
                app.preferences.setRestored(AppPreferences())
                app.preferences.set(previous)
                pending?.let { app.preferences.requestImportFolderRestore(it) }
            }
        }
    }
}
