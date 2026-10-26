package dev.soto.panelgrove

import android.app.Application
import androidx.room.Room
import dev.soto.panelgrove.data.*
import dev.soto.panelgrove.engine.DocumentEngine
import dev.soto.panelgrove.storage.SourceAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class PanelgroveApplication : Application() {
    val strings by lazy { dev.soto.panelgrove.ui.StringDictionary(resources) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val restoreIssue = MutableStateFlow<String?>(null)
    val database by lazy {
        val directory = getDatabasePath("panelgrove.db").parentFile!!
        check(directory.isDirectory || directory.mkdirs()) { strings[R.string.panelgrove_could_not_create_its_library_storage_check_available] }
        Room.databaseBuilder(this, LibraryDatabase::class.java, "panelgrove.db").addMigrations(LibraryDatabase.MIGRATION_1_2).build()
    }
    val preferences by lazy { PreferencesStore(this) }
    val sources by lazy { SourceAccess(this) }
    val engine by lazy { DocumentEngine(this, sources) }
    val library by lazy { LibraryRepository(this, database, preferences, sources, engine) }
    override fun onCreate() {
        super.onCreate()
        // Only startup removes leftovers: no import/render from this process can be using them yet.
        listOf(File(filesDir, "books"), File(filesDir, "covers"), File(cacheDir, "sources"), File(cacheDir, "pages")).forEach { directory ->
            directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".part") }.forEach { it.delete() }
        }
        File(cacheDir, "copy-imports").listFiles().orEmpty().filter { it.isFile }.forEach { it.delete() }
        applicationScope.launch(Dispatchers.IO) {
            runCatching { library.recoverRestore() }.onFailure {
                FailureMessages.recoverable(it, strings[R.string.backup_preferences_recovery_will_be_retried_on_next_launch])
                restoreIssue.value = strings[R.string.backup_preferences_still_need_restoring_check_free_storage_then]
            }
        }
    }
}
