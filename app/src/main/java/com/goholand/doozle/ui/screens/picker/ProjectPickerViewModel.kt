package com.goholand.doozle.ui.screens.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goholand.doozle.data.Project
import com.goholand.doozle.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectPickerViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showNameDialog = MutableStateFlow<String?>(null)
    /** Non-null when the "name your project" dialog should show. Value is the folder URI. */
    val showNameDialog: StateFlow<String?> = _showNameDialog

    fun onFolderSelected(folderUri: String) {
        _showNameDialog.value = folderUri
    }

    fun onProjectNameConfirmed(name: String) {
        val uri = _showNameDialog.value ?: return
        _showNameDialog.value = null
        viewModelScope.launch {
            repository.addProject(name, uri)
        }
    }

    fun onNameDialogDismissed() {
        _showNameDialog.value = null
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.removeProject(id)
        }
    }
}
