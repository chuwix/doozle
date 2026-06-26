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
        fun `initialize creates _unseen and _ranked directories`() {
            manager.initialize()

            assertTrue(fs.isDirectory("$projectRoot/_unseen"))
            assertTrue(fs.isDirectory("$projectRoot/_ranked"))
        }

        @Test
        fun `initialize is idempotent`() {
            manager.initialize()
            manager.initialize()

            assertTrue(fs.isDirectory("$projectRoot/_unseen"))
        }
    }

    @Nested
    inner class ScanForNew {

        @BeforeEach
        fun init() {
            manager.initialize()
        }

        @Test
        fun `scan moves new photos from root to _unseen`() {
            fs.createFile("$projectRoot/sunset.jpg")
            fs.createFile("$projectRoot/beach.png")

            val moved = manager.scanForNewPhotos()

            assertEquals(2, moved)
            assertTrue(fs.exists("$projectRoot/_unseen/sunset.jpg"))
            assertTrue(fs.exists("$projectRoot/_unseen/beach.png"))
            assertFalse(fs.exists("$projectRoot/sunset.jpg"))
            assertFalse(fs.exists("$projectRoot/beach.png"))
        }

        @Test
        fun `scan ignores _ranked and _unseen directories`() {
            fs.createFile("$projectRoot/_ranked/0_existing.jpg")
            fs.createFile("$projectRoot/_unseen/already_there.jpg")

            val moved = manager.scanForNewPhotos()

            assertEquals(0, moved)
        }

        @Test
        fun `scan ignores non-image files`() {
            fs.createFile("$projectRoot/document.pdf")
            fs.createFile("$projectRoot/notes.txt")
            fs.createFile("$projectRoot/photo.jpg")

            val moved = manager.scanForNewPhotos()

            assertEquals(1, moved)
            assertTrue(fs.exists("$projectRoot/_unseen/photo.jpg"))
            assertTrue(fs.exists("$projectRoot/document.pdf"))
        }

        @Test
        fun `scan finds photos in subdirectories recursively`() {
            fs.createFile("$projectRoot/subfolder/deep.jpg")
            fs.createFile("$projectRoot/subfolder/nested/deeper.png")

            val moved = manager.scanForNewPhotos()

            assertEquals(2, moved)
            assertTrue(fs.exists("$projectRoot/_unseen/deep.jpg"))
            assertTrue(fs.exists("$projectRoot/_unseen/deeper.png"))
        }

        @Test
        fun `scan skips _ranked and _unseen subdirectories`() {
            fs.createFile("$projectRoot/_ranked/0_existing.jpg")
            fs.createFile("$projectRoot/_unseen/already_there.jpg")
            fs.createFile("$projectRoot/other_subdir/new_photo.jpg")

            val moved = manager.scanForNewPhotos()

            assertEquals(1, moved)
            assertTrue(fs.exists("$projectRoot/_unseen/new_photo.jpg"))
        }

        @Test
        fun `scan does not duplicate if photo already in _unseen`() {
            fs.createFile("$projectRoot/_unseen/sunset.jpg")
            fs.createFile("$projectRoot/sunset.jpg")

            val moved = manager.scanForNewPhotos()

            // Should rename to avoid collision
            assertEquals(1, moved)
            assertTrue(fs.exists("$projectRoot/_unseen/sunset.jpg"))
        }
    }

    @Nested
    inner class UnseenList {

        @BeforeEach
        fun init() {
            manager.initialize()
        }

        @Test
        fun `getUnseenPhotos returns photos in _unseen folder`() {
            fs.createFile("$projectRoot/_unseen/a.jpg")
            fs.createFile("$projectRoot/_unseen/b.png")

            val unseen = manager.getUnseenPhotos()

            assertEquals(2, unseen.size)
            assertTrue(unseen.any { it.originalName == "a.jpg" })
            assertTrue(unseen.any { it.originalName == "b.png" })
        }

        @Test
        fun `getUnseenPhotos returns empty for no unseen`() {
            val unseen = manager.getUnseenPhotos()
            assertEquals(0, unseen.size)
        }

        @Test
        fun `hasUnseen returns true when photos exist`() {
            fs.createFile("$projectRoot/_unseen/photo.jpg")
            assertTrue(manager.hasUnseen())
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
        fun `promotePhoto removes from _unseen`() {
            fs.createFile("$projectRoot/_unseen/photo.jpg")
            val photo = Photo(originalName = "photo.jpg", path = "$projectRoot/_unseen/photo.jpg")

            manager.promotePhoto(photo)

            assertFalse(fs.exists("$projectRoot/_unseen/photo.jpg"))
        }

        @Test
        fun `promotePhoto returns path suitable for tree insertion`() {
            fs.createFile("$projectRoot/_unseen/photo.jpg")
            val photo = Photo(originalName = "photo.jpg", path = "$projectRoot/_unseen/photo.jpg")

            val newPath = manager.promotePhoto(photo)

            // The photo should be moved to a staging location for tree insertion
            assertTrue(fs.exists(newPath))
            assertFalse(fs.exists("$projectRoot/_unseen/photo.jpg"))
        }

        @Test
        fun `unseenCount decreases after promote`() {
            fs.createFile("$projectRoot/_unseen/a.jpg")
            fs.createFile("$projectRoot/_unseen/b.jpg")

            assertEquals(2, manager.getUnseenPhotos().size)

            val photo = Photo(originalName = "a.jpg", path = "$projectRoot/_unseen/a.jpg")
            manager.promotePhoto(photo)

            assertEquals(1, manager.getUnseenPhotos().size)
        }
    }
}
