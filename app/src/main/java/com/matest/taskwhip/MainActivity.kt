package com.matest.taskwhip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.unit.dp


import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.lifecycle.viewmodel.compose.viewModel

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    CHECKLIST, PROGRESS
}

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
        setContent { 
            val viewModel: TaskWhipViewModel = viewModel()
            TaskWhipUI(viewModel) 
        }
    }
}

@Composable
fun TaskWhipUI(viewModel: TaskWhipViewModel) {
    var title by remember { mutableStateOf("") }
    var goalText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TaskType.CHECKLIST) }
    val tasks by viewModel.tasks.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(top = topInset, start = 16.dp, end = 16.dp, bottom = 16.dp)) {

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
            if (title.isNotBlank()) {
                val goal = goalText.toIntOrNull()
                viewModel.addTask(title, selectedType, goal)
                title = ""
                goalText = ""
                selectedType = TaskType.CHECKLIST
            }
        }, modifier = Modifier.align(Alignment.End)) {
            Text("Add Task")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Your Tasks", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            items(tasks) { task ->
                if (task.type == TaskType.CHECKLIST) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = task.isDone,
                            onCheckedChange = { viewModel.updateTask(task, it) }
                        )
                        Text(task.title, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.deleteTask(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Task")
                        }
                    }
                } else {
                    val percent = ((task.currentProgress ?: 0).toFloat() / (task.goal ?: 1).toFloat()).coerceIn(0f, 1f)
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("${task.title} (${(percent * 100).toInt()}%)")
                            LinearProgressIndicator(progress = { percent })
                            Text("${task.currentProgress}/${task.goal}")
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(onClick = { viewModel.incrementProgress(task) }) {
                                Text("Add Progress")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(onClick = { viewModel.deleteTask(task) }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskTypeDropdown(
    selectedType: TaskType,
    onSelect: (TaskType) -> Unit
) {
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
