package com.matest.taskwhip

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher


val Context.dataStore by preferencesDataStore("taskwhip_store")
val TASK_LIST_KEY = stringPreferencesKey("task_list")

@Serializable
enum class TaskType { CHECKLIST, PROGRESS }

@Serializable
data class TaskItem(
    val title: String,
    val type: TaskType,
    val isDone: Boolean = false,
    val goal: Int? = null,
    val currentProgress: Int? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TaskWhipApp() }
    }
}

@Composable
fun TaskWhipApp() {
    val context = LocalContext.current
    val tasks = remember { mutableStateListOf<TaskItem>() }

    // Load tasks on launch
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val rawJson = prefs[TASK_LIST_KEY]
        runCatching {
            rawJson?.let {
                val loaded = Json.decodeFromString<List<TaskItem>>(it)
                tasks.clear()
                tasks.addAll(loaded)
            }
        }.onFailure {
            tasks.clear()
            saveTasks(context, tasks)
        }
    }

    var title by remember { mutableStateOf("") }
    var goalText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TaskType.CHECKLIST) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(16.dp)
    ) {
        TextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Task Title") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TaskTypeDropdown(selectedType) { selectedType = it }

        if (selectedType == TaskType.PROGRESS) {
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = goalText,
                onValueChange = { goalText = it },
                label = { Text("Goal Value") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            if (title.isBlank()) return@Button
            val goal = goalText.toIntOrNull()
            if (selectedType == TaskType.PROGRESS && goal == null) return@Button

            tasks.add(
                TaskItem(
                    title = title,
                    type = selectedType,
                    isDone = false,
                    goal = if (selectedType == TaskType.PROGRESS) goal else null,
                    currentProgress = if (selectedType == TaskType.PROGRESS) 0 else null
                )
            )
            title = ""
            goalText = ""
            selectedType = TaskType.CHECKLIST
            saveTasks(context, tasks)
        }, modifier = Modifier.align(Alignment.End)) {
            Text("Add Task")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Your Tasks", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(tasks) { task ->
                Spacer(modifier = Modifier.height(4.dp))

                if (task.type == TaskType.CHECKLIST) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = {
                                val index = tasks.indexOf(task)
                                if (index != -1) {
                                    tasks[index] = task.copy(isDone = it)
                                    saveTasks(context, tasks)
                                }
                            }
                        )
                        Text(task.title)
                    }
                } else {
                    val percent = ((task.currentProgress ?: 0).toFloat() / (task.goal ?: 1)).coerceIn(0f, 1f)

                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("${task.title} (${(percent * 100).toInt()}%)")
                            Box(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(progress = {percent}, modifier = Modifier.fillMaxWidth())
                            }
                            Text("${task.currentProgress}/${task.goal}")

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(onClick = {
                                    tasks.remove(task)
                                    saveTasks(context, tasks)
                                }) {
                                    Text("Delete", color = Color.Red)
                                }
                                Button(onClick = {
                                    val updatedProgress = (task.currentProgress ?: 0) + 1
                                    val index = tasks.indexOf(task)
                                    if (index != -1) {
                                        tasks[index] = task.copy(currentProgress = updatedProgress)
                                        saveTasks(context, tasks)
                                    }
                                }) {
                                    Text("Add Progress")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskTypeDropdown(selectedType: TaskType, onSelect: (TaskType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(onClick = { expanded = true }) {
            Text(if (selectedType == TaskType.CHECKLIST) "Checklist" else "Progress Goal")
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Checklist") }, onClick = {
                onSelect(TaskType.CHECKLIST)
                expanded = false
            })
            DropdownMenuItem(text = { Text("Progress Goal") }, onClick = {
                onSelect(TaskType.PROGRESS)
                expanded = false
            })
        }
    }
}

fun saveTasks(context: Context, tasks: List<TaskItem>) {
    val scope = CoroutineScope(context = context.mainExecutor.asCoroutineDispatcher())
    scope.launch {
        val json = Json.encodeToString(tasks)
        context.dataStore.edit { prefs ->
            prefs[TASK_LIST_KEY] = json
        }
    }
}
