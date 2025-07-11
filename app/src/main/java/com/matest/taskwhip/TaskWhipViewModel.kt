package com.matest.taskwhip

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskWhipViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TasksRepository(application)

    val tasks = repository.tasks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addTask(title: String, type: TaskType, goal: Int? = null) {
        viewModelScope.launch {
            val newTask = TaskItem(
                title = title,
                type = type,
                goal = if (type == TaskType.PROGRESS) goal else null,
                currentProgress = if (type == TaskType.PROGRESS) 0 else null
            )
            repository.saveTasks(tasks.value + newTask)
        }
    }

    fun updateTask(task: TaskItem, isDone: Boolean) {
        viewModelScope.launch {
            val updatedTasks = tasks.value.map {
                if (it.title == task.title) {
                    it.copy(isDone = isDone)
                } else {
                    it
                }
            }
            repository.saveTasks(updatedTasks)
        }
    }

    fun incrementProgress(task: TaskItem) {
        viewModelScope.launch {
            val updatedTasks = tasks.value.map {
                if (it.title == task.title && (it.currentProgress ?: 0) < (it.goal ?: 1)) {
                    it.copy(currentProgress = (it.currentProgress ?: 0) + 1)
                } else {
                    it
                }
            }
            repository.saveTasks(updatedTasks)
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch {
            repository.saveTasks(tasks.value.filter { it.title != task.title })
        }
    }
}
