package com.matest.taskwhip

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.work.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import com.matest.taskwhip.ui.theme.TaskWhipTheme
import androidx.compose.foundation.background


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
        requestNotificationPermission()
        scheduleReminderWork()
        setContent {
            TaskWhipTheme {
                TaskWhipApp()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun scheduleReminderWork() {
        val request = PeriodicWorkRequestBuilder<TaskWhipReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "taskwhip_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

@Composable
fun TaskWhipApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks = remember { mutableStateListOf<TaskItem>() }

    LaunchedEffect(Unit) {
        val rawJson = context.dataStore.data.first()[TASK_LIST_KEY]
        runCatching {
            val loaded = rawJson?.let { Json.decodeFromString<List<TaskItem>>(it) }
            if (loaded != null) {
                tasks.clear()
                tasks.addAll(loaded)
            }
        }
    }

    var title by remember { mutableStateOf("") }
    var goalText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TaskType.CHECKLIST) }

    fun saveTasks() {
        scope.launch {
            val json = Json.encodeToString(tasks.toList())
            context.dataStore.edit { it[TASK_LIST_KEY] = json }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 👈 Add this line
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(16.dp)
    )
 {
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
            if (selectedType == TaskType.PROGRESS && goal == null) {
                Toast.makeText(context, "Please enter a valid goal", Toast.LENGTH_SHORT).show()
                return@Button
            }

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
            saveTasks()
        }, modifier = Modifier.align(Alignment.End)) {
            Text("Add Task")
        }

        Spacer(modifier = Modifier.height(16.dp))
     Text(
         text = "Your Tasks",
         style = MaterialTheme.typography.titleLarge,
         color = MaterialTheme.colorScheme.onBackground
     )

     Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(tasks) { task ->
                Spacer(modifier = Modifier.height(4.dp))

                if (task.type == TaskType.CHECKLIST) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = task.isDone,
                                onCheckedChange = {
                                    val index = tasks.indexOf(task)
                                    if (index != -1) {
                                        tasks[index] = task.copy(isDone = it)
                                        saveTasks()
                                    }
                                }
                            )
                            Text(
                                text = task.title,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                        }
                        IconButton(onClick = {
                            val index = tasks.indexOf(task)
                            if (index != -1) {
                                tasks.removeAt(index)
                                saveTasks()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                } else {
                    val percent = ((task.currentProgress ?: 0).toFloat() / (task.goal ?: 1)).coerceIn(0f, 1f)
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("${task.title} (${(percent * 100).toInt()}%)")
                            Box(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { percent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                            Text("${task.currentProgress}/${task.goal}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(onClick = {
                                    val index = tasks.indexOf(task)
                                    if (index != -1) {
                                        tasks.removeAt(index)
                                        saveTasks()
                                    }
                                }) {
                                    Text("Delete", color = Color.Red)
                                }
                                Button(onClick = {
                                    val index = tasks.indexOf(task)
                                    if (index != -1) {
                                        val updated = (task.currentProgress ?: 0) + 1
                                        val capped = updated.coerceAtMost(task.goal ?: 1)
                                        tasks[index] = task.copy(currentProgress = capped)
                                        saveTasks()
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
