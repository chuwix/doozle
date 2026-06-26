package com.goholand.doozle.data

import kotlinx.serialization.Serializable

/**
 * A Doozle project: a folder of photos to be ranked via pairwise comparison.
 *
 * @param id Unique identifier (UUID string)
 * @param name Display name chosen by user
 * @param folderUri SAF URI string for the selected folder (persisted permission)
 * @param createdAt Epoch millis when project was created
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val folderUri: String,
    val createdAt: Long
)

/**
 * Persisted list of all projects.
 */
@Serializable
data class ProjectList(
    val projects: List<Project> = emptyList()
)
