package com.goholand.doozle.data

import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing the list of Doozle projects.
 * Persists as a JSON file in app-internal storage.
 */
interface ProjectRepository {
    /** Observe the current list of projects. */
    fun observeProjects(): Flow<List<Project>>

    /** Get all projects (one-shot). */
    suspend fun getProjects(): List<Project>

    /** Add a new project. Returns the created project. */
    suspend fun addProject(name: String, folderUri: String): Project

    /** Remove a project by ID. */
    suspend fun removeProject(id: String)

    /** Update a project's name. */
    suspend fun renameProject(id: String, newName: String)
}
