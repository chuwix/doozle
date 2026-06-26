package com.goholand.doozle.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * File-based implementation of ProjectRepository.
 * Stores project list as JSON in [storageDir]/projects.json.
 */
class ProjectRepositoryImpl(
    private val storageDir: File,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }
) : ProjectRepository {

    private val file get() = File(storageDir, "projects.json")
    private val mutex = Mutex()
    private val _projects = MutableStateFlow<List<Project>>(emptyList())

    init {
        _projects.value = loadFromDisk()
    }

    override fun observeProjects(): Flow<List<Project>> = _projects.asStateFlow()

    override suspend fun getProjects(): List<Project> = _projects.value

    override suspend fun addProject(name: String, folderUri: String): Project {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            folderUri = folderUri,
            createdAt = System.currentTimeMillis()
        )
        mutex.withLock {
            val current = _projects.value.toMutableList()
            current.add(project)
            saveToDisk(current)
            _projects.value = current
        }
        return project
    }

    override suspend fun removeProject(id: String) {
        mutex.withLock {
            val current = _projects.value.filter { it.id != id }
            saveToDisk(current)
            _projects.value = current
        }
    }

    override suspend fun renameProject(id: String, newName: String) {
        mutex.withLock {
            val current = _projects.value.map {
                if (it.id == id) it.copy(name = newName) else it
            }
            saveToDisk(current)
            _projects.value = current
        }
    }

    private fun loadFromDisk(): List<Project> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            json.decodeFromString<ProjectList>(text).projects
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(projects: List<Project>) {
        storageDir.mkdirs()
        val data = json.encodeToString(ProjectList.serializer(), ProjectList(projects))
        file.writeText(data)
    }
}
