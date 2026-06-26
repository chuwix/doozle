package com.goholand.doozle.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var repo: ProjectRepositoryImpl

    @BeforeEach
    fun setup() {
        repo = ProjectRepositoryImpl(tempDir)
    }

    @Nested
    inner class BasicOperations {

        @Test
        fun `initially returns empty list`() = runTest {
            assertEquals(emptyList<Project>(), repo.getProjects())
        }

        @Test
        fun `addProject creates project with correct fields`() = runTest {
            val project = repo.addProject("Vacation", "content://folder/vacation")

            assertEquals("Vacation", project.name)
            assertEquals("content://folder/vacation", project.folderUri)
            assertTrue(project.id.isNotBlank())
            assertTrue(project.createdAt > 0)
        }

        @Test
        fun `addProject makes project appear in list`() = runTest {
            repo.addProject("Vacation", "content://folder/vacation")

            val projects = repo.getProjects()
            assertEquals(1, projects.size)
            assertEquals("Vacation", projects[0].name)
        }

        @Test
        fun `multiple projects are stored in order`() = runTest {
            repo.addProject("First", "content://folder/first")
            repo.addProject("Second", "content://folder/second")
            repo.addProject("Third", "content://folder/third")

            val projects = repo.getProjects()
            assertEquals(3, projects.size)
            assertEquals("First", projects[0].name)
            assertEquals("Second", projects[1].name)
            assertEquals("Third", projects[2].name)
        }

        @Test
        fun `removeProject removes by id`() = runTest {
            val p1 = repo.addProject("Keep", "content://a")
            val p2 = repo.addProject("Remove", "content://b")

            repo.removeProject(p2.id)

            val projects = repo.getProjects()
            assertEquals(1, projects.size)
            assertEquals("Keep", projects[0].name)
        }

        @Test
        fun `removeProject with unknown id does nothing`() = runTest {
            repo.addProject("Stay", "content://a")

            repo.removeProject("nonexistent-id")

            assertEquals(1, repo.getProjects().size)
        }

        @Test
        fun `renameProject updates name`() = runTest {
            val project = repo.addProject("Old Name", "content://a")

            repo.renameProject(project.id, "New Name")

            val projects = repo.getProjects()
            assertEquals("New Name", projects[0].name)
            assertEquals(project.folderUri, projects[0].folderUri)
        }
    }

    @Nested
    inner class Persistence {

        @Test
        fun `projects survive repo recreation`() = runTest {
            repo.addProject("Persistent", "content://folder/persist")

            // Create a new repo instance pointing to same directory
            val repo2 = ProjectRepositoryImpl(tempDir)

            val projects = repo2.getProjects()
            assertEquals(1, projects.size)
            assertEquals("Persistent", projects[0].name)
        }

        @Test
        fun `corrupted file returns empty list`() = runTest {
            File(tempDir, "projects.json").writeText("not valid json {{{")

            val repo2 = ProjectRepositoryImpl(tempDir)
            assertEquals(emptyList<Project>(), repo2.getProjects())
        }

        @Test
        fun `empty file returns empty list`() = runTest {
            File(tempDir, "projects.json").writeText("")

            val repo2 = ProjectRepositoryImpl(tempDir)
            assertEquals(emptyList<Project>(), repo2.getProjects())
        }
    }

    @Nested
    inner class Flow {

        @Test
        fun `observeProjects emits current state`() = runTest {
            repo.addProject("A", "content://a")

            val projects = repo.observeProjects().first()
            assertEquals(1, projects.size)
            assertEquals("A", projects[0].name)
        }

        @Test
        fun `observeProjects emits updates after add`() = runTest {
            val flow = repo.observeProjects()

            assertEquals(0, flow.first().size)

            repo.addProject("New", "content://new")

            assertEquals(1, flow.first().size)
        }
    }
}
