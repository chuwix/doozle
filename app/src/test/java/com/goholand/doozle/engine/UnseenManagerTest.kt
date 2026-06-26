package com.goholand.doozle.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UnseenManagerTest {

    private lateinit var fs: InMemoryFileSystem
    private lateinit var manager: UnseenManager
    private val projectRoot = "project"

    @BeforeEach
    fun setup() {
        fs = InMemoryFileSystem()
        manager = UnseenManager(fs, projectRoot)
    }

    @Nested
    inner class Initialization {

        @Test
        fun `initialize creates _ranked directory`() {
            manager.initialize()

            assertTrue(fs.isDirectory("$projectRoot/_ranked"))
        }

        @Test
        fun `initialize is idempotent`() {
            manager.initialize()
            manager.initialize()

            assertTrue(fs.isDirectory("$projectRoot/_ranked"))
        }
    }

    @Nested
    inner class UnseenDetection {

        @BeforeEach
        fun init() {
            manager.initialize()
        }

        @Test
        fun `photos in project root are unseen`() {
            fs.createFile("$projectRoot/sunset.jpg")
            fs.createFile("$projectRoot/beach.png")

            val unseen = manager.getUnseenPhotos()

            assertEquals(2, unseen.size)
            assertTrue(unseen.any { it.originalName == "sunset.jpg" })
            assertTrue(unseen.any { it.originalName == "beach.png" })
        }

        @Test
        fun `photos in _ranked are NOT unseen`() {
            fs.createFile("$projectRoot/_ranked/0_existing.jpg")

            val unseen = manager.getUnseenPhotos()

            assertEquals(0, unseen.size)
        }

        @Test
        fun `non-image files are not unseen`() {
            fs.createFile("$projectRoot/document.pdf")
            fs.createFile("$projectRoot/notes.txt")
            fs.createFile("$projectRoot/photo.jpg")

            val unseen = manager.getUnseenPhotos()

            assertEquals(1, unseen.size)
            assertEquals("photo.jpg", unseen[0].originalName)
        }

        @Test
        fun `photos in subdirectories are found recursively`() {
            fs.createFile("$projectRoot/subfolder/deep.jpg")
            fs.createFile("$projectRoot/subfolder/nested/deeper.png")

            val unseen = manager.getUnseenPhotos()

            assertEquals(2, unseen.size)
            assertTrue(unseen.any { it.originalName == "deep.jpg" })
            assertTrue(unseen.any { it.originalName == "deeper.png" })
        }

        @Test
        fun `photos in _ranked subdirectories are excluded`() {
            fs.createFile("$projectRoot/_ranked/0_existing.jpg")
            fs.createFile("$projectRoot/other_subdir/new_photo.jpg")

            val unseen = manager.getUnseenPhotos()

            assertEquals(1, unseen.size)
            assertEquals("new_photo.jpg", unseen[0].originalName)
        }

        @Test
        fun `hidden files are excluded`() {
            fs.createFile("$projectRoot/.hidden.jpg")
            fs.createFile("$projectRoot/visible.jpg")

            val unseen = manager.getUnseenPhotos()

            assertEquals(1, unseen.size)
            assertEquals("visible.jpg", unseen[0].originalName)
        }

        @Test
        fun `hasUnseen returns true when photos exist outside _ranked`() {
            fs.createFile("$projectRoot/photo.jpg")
            assertTrue(manager.hasUnseen())
        }

        @Test
        fun `hasUnseen returns false when no photos outside _ranked`() {
            fs.createFile("$projectRoot/_ranked/0_photo.jpg")
            assertFalse(manager.hasUnseen())
        }

        @Test
        fun `hasUnseen returns false when empty`() {
            assertFalse(manager.hasUnseen())
        }
    }

    @Nested
    inner class PromoteToRanked {

        @BeforeEach
        fun init() {
            manager.initialize()
        }

        @Test
        fun `promotePhoto moves from original location`() {
            fs.createFile("$projectRoot/photo.jpg")
            val photo = Photo(originalName = "photo.jpg", path = "$projectRoot/photo.jpg")

            manager.promotePhoto(photo)

            assertFalse(fs.exists("$projectRoot/photo.jpg"))
        }

        @Test
        fun `promotePhoto returns staging path suitable for tree insertion`() {
            fs.createFile("$projectRoot/photo.jpg")
            val photo = Photo(originalName = "photo.jpg", path = "$projectRoot/photo.jpg")

            val newPath = manager.promotePhoto(photo)

            assertTrue(fs.exists(newPath))
            assertFalse(fs.exists("$projectRoot/photo.jpg"))
        }

        @Test
        fun `promotePhoto works with nested files`() {
            fs.createFile("$projectRoot/vacation/photo.jpg")
            val photo = Photo(originalName = "photo.jpg", path = "$projectRoot/vacation/photo.jpg")

            val newPath = manager.promotePhoto(photo)

            assertTrue(fs.exists(newPath))
            assertFalse(fs.exists("$projectRoot/vacation/photo.jpg"))
        }

        @Test
        fun `unseen count decreases after promote`() {
            fs.createFile("$projectRoot/a.jpg")
            fs.createFile("$projectRoot/b.jpg")

            assertEquals(2, manager.getUnseenPhotos().size)

            val photo = Photo(originalName = "a.jpg", path = "$projectRoot/a.jpg")
            manager.promotePhoto(photo)

            assertEquals(1, manager.getUnseenPhotos().size)
        }
    }

    @Nested
    inner class EmptyProjectRoot {
        private lateinit var emptyRootManager: UnseenManager

        @BeforeEach
        fun init() {
            emptyRootManager = UnseenManager(fs, "")
            emptyRootManager.initialize()
        }

        @Test
        fun `works with empty project root`() {
            fs.createFile("photo.jpg")

            val unseen = emptyRootManager.getUnseenPhotos()

            assertEquals(1, unseen.size)
            assertEquals("photo.jpg", unseen[0].originalName)
        }

        @Test
        fun `skips _ranked with empty project root`() {
            fs.createFile("_ranked/0_photo.jpg")
            fs.createFile("other.jpg")

            val unseen = emptyRootManager.getUnseenPhotos()

            assertEquals(1, unseen.size)
            assertEquals("other.jpg", unseen[0].originalName)
        }
    }
}
