package com.lpscanner.app.database

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SelectedSheet(val id: String, val name: String)

private val Context.settings by preferencesDataStore(name = "settings")

/**
 * Remembers which spreadsheet is the plate database, per Google account. Only the ID and
 * name are stored on the device; plate data stays in Google Sheets.
 */
class SelectedSheetStore(private val context: Context) {
    suspend fun get(email: String): SelectedSheet? {
        val prefs = context.settings.data.first()
        val id = prefs[idKey(email)] ?: return null
        return SelectedSheet(id, prefs[nameKey(email)].orEmpty())
    }

    suspend fun set(email: String, sheet: SelectedSheet) {
        context.settings.edit {
            it[idKey(email)] = sheet.id
            it[nameKey(email)] = sheet.name
        }
    }

    suspend fun clear(email: String) {
        context.settings.edit {
            it.remove(idKey(email))
            it.remove(nameKey(email))
        }
    }

    /**
     * Sheets opened from a link, newest first. The app can't list those from Drive (it only
     * sees the sheets it created), so the picker shows them from here.
     */
    suspend fun linked(email: String): List<SelectedSheet> {
        val json = context.settings.data.first()[linkedKey(email)] ?: return emptyList()
        return runCatching { Json.decodeFromString(linkedSerializer, json) }.getOrDefault(emptyList())
    }

    suspend fun addLinked(email: String, sheet: SelectedSheet) = editLinked(email) { list ->
        listOf(sheet) + list.filter { it.id != sheet.id }
    }

    suspend fun removeLinked(email: String, id: String) = editLinked(email) { list -> list.filter { it.id != id } }

    private suspend fun editLinked(email: String, change: (List<SelectedSheet>) -> List<SelectedSheet>) {
        val updated = change(linked(email)).take(MAX_LINKED)
        context.settings.edit { it[linkedKey(email)] = Json.encodeToString(linkedSerializer, updated) }
    }

    private fun idKey(email: String) = stringPreferencesKey("sheet_id:$email")
    private fun nameKey(email: String) = stringPreferencesKey("sheet_name:$email")
    private fun linkedKey(email: String) = stringPreferencesKey("linked_sheets:$email")

    private companion object {
        const val MAX_LINKED = 20
        val linkedSerializer = ListSerializer(SelectedSheet.serializer())
    }
}
