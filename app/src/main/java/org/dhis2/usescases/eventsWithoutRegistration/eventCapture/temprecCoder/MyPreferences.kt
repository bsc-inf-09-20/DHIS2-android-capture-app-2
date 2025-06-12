// File: MyPreferences.kt

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level property — OUTSIDE any class
val Context.dataStore by preferencesDataStore(name = "settings")

class MyPreferences(private val context: Context) {

    companion object {
        private val TEMP_DATA_KEY = stringPreferencesKey("temp")
    }

    //  Flow to observe stored value
    val TempDataFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[TEMP_DATA_KEY]
        }

    //  Save function
    suspend fun saveTempData(name: String) {
        context.dataStore.edit { preferences ->
            preferences[TEMP_DATA_KEY] = name
        }
    }
}
