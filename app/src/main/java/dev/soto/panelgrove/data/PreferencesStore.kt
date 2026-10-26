package dev.soto.panelgrove.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.soto.panelgrove.core.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.panelgrovePreferences by preferencesDataStore("panelgrove")
class PreferencesStore(context: Context) {
    private val store = context.panelgrovePreferences
    private val key = stringPreferencesKey("preferences-v1")
    private val acknowledged = stringSetPreferencesKey("acknowledged-import-failures")
    private val restoreFolder = stringPreferencesKey("pending-import-folder-restore")
    private val data = store.data.catch { if (it is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw it }
    val acknowledgedFailures = data.map { it[acknowledged].orEmpty() }
    // Device permission grants are deliberately reacquired after a backup restore.
    // This lives outside exported preferences, so recovery also survives process death.
    val pendingImportFolderRestore = data.map { it[restoreFolder] }
    suspend fun requestImportFolderRestore(uri: String?) { store.edit { it[restoreFolder] = uri.orEmpty() } }
    suspend fun finishImportFolderRestore(uri: String) { store.edit { if (it[restoreFolder] == uri) it.remove(restoreFolder) } }
    suspend fun setRestored(value: AppPreferences) {
        store.edit {
            it[key] = PanelgroveJson.encodeToString(value)
            if (value.copyFolderUri != null || value.copyImports) it[restoreFolder] = value.copyFolderUri.orEmpty()
            else it.remove(restoreFolder)
        }
    }
    suspend fun acknowledgeFailure(id: String) { store.edit { it[acknowledged] = (it[acknowledged].orEmpty() + id).toList().takeLast(200).toSet() } }
    val flow = data
        .map { prefs -> prefs[key]?.let { PanelgroveJson.decodeFromString<AppPreferences>(it) } ?: AppPreferences() }
    suspend fun current() = flow.first()
    // Replacing preferences discards recovery for the previous configuration.
    // Backup restoration uses setRestored to install both values atomically.
    suspend fun set(value: AppPreferences) { store.edit { it[key] = PanelgroveJson.encodeToString(value); it.remove(restoreFolder) } }
    suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        store.edit { prefs ->
            val old = prefs[key]?.let { PanelgroveJson.decodeFromString<AppPreferences>(it) } ?: AppPreferences()
            prefs[key] = PanelgroveJson.encodeToString(transform(old))
        }
    }
}
