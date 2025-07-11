package com.matest.taskwhip

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tasks")

class TasksRepository(private val context: Context) {

    private val tasksKey = stringPreferencesKey("tasks")

    val tasks: Flow<List<TaskItem>> = context.dataStore.data
        .map {
            val json = it[tasksKey] ?: "[]"
            Json.decodeFromString<List<TaskItem>>(json)
        }

    suspend fun saveTasks(tasks: List<TaskItem>) {
        context.dataStore.edit {
            it[tasksKey] = Json.encodeToString(tasks)
        }
    }
}
