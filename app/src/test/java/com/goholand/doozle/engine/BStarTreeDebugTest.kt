package com.goholand.doozle.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Focused debug tests to trace through B* tree operations step by step.
 */
class BStarTreeDebugTest {

    private val config = EngineConfig(order = 10)
    private val rankedRoot = "project/_ranked"

    @Test
    fun `debug - insert 10 photos and inspect filesystem state`() {
        val fs = InMemoryFileSystem()
        val tree = BStarTree(fs, rankedRoot, config)
        tree.initialize()

        // Insert 9 photos (max per leaf)
        for (i in 0 until 9) {
            val path = "project/_unseen/photo_$i.jpg"
            fs.createFile(path)
            tree.insertAt(path, i)
        }

        assertEquals(9, tree.totalPhotos(), "Should have 9 photos before split")
        assertEquals(1, tree.totalLeaves(), "Should have 1 leaf before split")

        // Now insert the 10th
        val path10 = "project/_unseen/photo_9.jpg"
        fs.createFile(path10)
        tree.insertAt(path10, 9)

        // Debug: print filesystem state
        println("After inserting 10 photos:")
        println(fs.dump())
        println("totalPhotos: ${tree.totalPhotos()}")
        println("totalLeaves: ${tree.totalLeaves()}")
        println("depth: ${tree.depth()}")
        println("allPhotos: ${tree.allPhotosInOrder().map { it.originalName }}")

        assertEquals(10, tree.totalPhotos(), "Should have 10 photos after split")
        assertTrue(tree.totalLeaves() > 1, "Should have split into multiple leaves")
    }

    @Test
    fun `debug - position lookup in single leaf`() {
        val fs = InMemoryFileSystem()
        val tree = BStarTree(fs, rankedRoot, config)
        tree.initialize()

        for (i in 0 until 5) {
            val path = "project/_unseen/photo_$i.jpg"
            fs.createFile(path)
            tree.insertAt(path, i)
        }

        println("Filesystem state (5 photos):")
        println(fs.dump())

        for (i in 0 until 5) {
            val photo = tree.photoAt(i)
            println("Position $i: ${photo.originalName} at ${photo.path}")
            assertEquals("photo_$i.jpg", photo.originalName, "Photo at position $i")
        }
    }

    @Test
    fun `debug - removal from single leaf`() {
        val fs = InMemoryFileSystem()
        val tree = BStarTree(fs, rankedRoot, config)
        tree.initialize()

        for (i in 0 until 5) {
            val path = "project/_unseen/photo_$i.jpg"
            fs.createFile(path)
            tree.insertAt(path, i)
        }

        val removed = tree.removeAt(2)
        println("Removed: ${removed.originalName}")
        println("Remaining: ${tree.allPhotosInOrder().map { it.originalName }}")
        println("Filesystem after removal:")
        println(fs.dump())

        assertEquals("photo_2.jpg", removed.originalName)
        assertEquals(4, tree.totalPhotos())
    }
}
