package dev.soto.panelgrove.storage

import dev.soto.panelgrove.R
import android.content.Context
import android.net.Uri
import androidx.work.*
import dev.soto.panelgrove.PanelgroveApplication
import dev.soto.panelgrove.FailureMessages
import dev.soto.panelgrove.BuildConfig
import kotlinx.coroutines.CancellationException

class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val strings get() = dev.soto.panelgrove.ui.StringDictionary(applicationContext.resources)
    override suspend fun doWork(): Result {
        val library = (applicationContext as PanelgroveApplication).library
        val progress: suspend (String) -> Unit = { setProgress(workDataOf("message" to it)) }
        return try {
            val rootId = inputData.getString("root")
            val restoreFolder = inputData.getString("restoreFolder")
            if (restoreFolder != null) {
                val result = library.recoverImportFolder(Uri.parse(restoreFolder), progress)
                library.preferences.finishImportFolderRestore(restoreFolder)
                Result.success(workDataOf("message" to strings[R.string.import_folder_restored,
                    strings.quantity(R.plurals.recovered_book_count, result.restored, result.restored),
                    strings.quantity(R.plurals.unreadable_file_count, result.failed, result.failed)]))
            } else if (inputData.getBoolean("moveCopies", false)) {
                library.moveCopies(progress)
                Result.success(workDataOf("message" to strings[R.string.manga_copies_moved_into_the_selected_import_folder]))
            } else if (rootId != null) {
                val errors = library.scan(rootId, progress)
                errors.forEach { android.util.Log.w("PanelgroveScan", it) }
                Result.success(workDataOf("message" to if (BuildConfig.DEBUG && errors.isNotEmpty()) errors.joinToString("\n").take(7000) else strings[R.string.library_updated]))
            } else {
                val uri = Uri.parse(inputData.getString("uri") ?: return Result.failure())
                val book = library.importFile(uri, copy = inputData.getBoolean("copy", false), replaceId = inputData.getString("replace"),
                    linkOnly = inputData.getBoolean("relink", false), progress = progress)
                Result.success(workDataOf("message" to strings[if (inputData.getBoolean("relink", false)) R.string.relinked_book else R.string.added, book.title], "bookId" to book.id))
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val fallback = if (inputData.getString("restoreFolder") != null) strings[R.string.import_folder_restore_failed]
                else if (inputData.getBoolean("relink", false)) strings[R.string.relink_failed]
                else if (inputData.getBoolean("moveCopies", false)) strings[R.string.copies_could_not_be_moved_check_folder_access_and]
                else strings[R.string.import_failed_check_that_the_file_is_a_readable]
            val message = FailureMessages.user(error, fallback)
            Result.failure(workDataOf("message" to (if (error is dev.soto.panelgrove.data.RelinkConflictException) error.message ?: message else message).take(7000),
                "background" to inputData.getBoolean("background", false), "requestedAt" to inputData.getLong("requestedAt", 0)))
        }
    }
    companion object {
        fun file(context: Context, uri: Uri, copy: Boolean, replace: String? = null, relink: Boolean = false): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(workDataOf("uri" to uri.toString(), "copy" to copy, "replace" to replace,
                "relink" to relink, "requestedAt" to System.currentTimeMillis())).addTag("library").build()
            WorkManager.getInstance(context).enqueueUniqueWork("import:${digest(uri.toString())}:$replace", ExistingWorkPolicy.KEEP, request)
            return request.id
        }
        fun scan(context: Context, root: String) {
            val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(workDataOf("root" to root, "background" to true)).addTag("library").build()
            WorkManager.getInstance(context).enqueueUniqueWork("scan:${root}", ExistingWorkPolicy.KEEP, request)
        }
        fun restoreFolder(context: Context, uri: Uri): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(workDataOf("restoreFolder" to uri.toString(),
                "requestedAt" to System.currentTimeMillis())).addTag("library").build()
            WorkManager.getInstance(context).enqueueUniqueWork("restore-import-folder", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            return request.id
        }
        fun moveCopies(context: Context): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(workDataOf("moveCopies" to true, "requestedAt" to System.currentTimeMillis())).addTag("library").build()
            WorkManager.getInstance(context).enqueueUniqueWork("move-copies", ExistingWorkPolicy.KEEP, request)
            return request.id
        }
    }
}
