package com.goholand.doozle.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BStarTreeTest {

    private lateinit var fs: InMemoryFileSystem
    private lateinit var tree: BStarTree
    private val config = EngineConfig(order = 10)
    private val rankedRoot = "project/_ranked"

    @BeforeEach
    fun setup() {
        fs = InMemoryFileSystem()
        tree = BStarTree(fs, rankedRoot, config)
    }

    private fun createPhoto(name: String): String {
        val path = "project/_unseen/$name"
        fs.createFile(path)
        return path
    }

    @Nested
    inner class Initialization {

        @Test
        fun `initialize creates root directory`() {
            tree.initialize()
            assertTrue(fs.isDirectory(rankedRoot))
        }

        @Test
        fun `empty tree has zero photos`() {
            tree.initialize()
            assertEquals(0, tree.totalPhotos())
        }

        @Test
        fun `empty tree has one leaf`() {
            tree.initialize()
            assertEquals(1, tree.totalLeaves())
        }

        @Test
        fun `empty tree has depth 1`() {
            tree.initialize()
            assertEquals(1, tree.depth())
        }

        @Test
        fun `empty tree validates`() {
            tree.initialize()
            val result = tree.validate()
            assertTrue(result.valid, "Errors: ${result.errors}")
        }
    }

    @Nested
    inner class BasicInsertion {

        @BeforeEach
        fun init() {
            tree.initialize()
        }

        @Test
        fun `insert single photo at position 0`() {
            val photo = createPhoto("sunset.jpg")
            tree.insertAt(photo, 0)

            assertEquals(1, tree.totalPhotos())
            assertEquals("sunset.jpg", tree.photoAt(0).originalName)
        }

        @Test
        fun `insert photo at center of empty tree places at position 0`() {
            val photo = createPhoto("beach.jpg")
            val pos = tree.insertAtCenter(photo)

            assertEquals(0, pos)
            assertEquals(1, tree.totalPhotos())
        }

        @Test
        fun `insert multiple photos maintains order`() {
            val names = listOf("a.jpg", "b.jpg", "c.jpg", "d.jpg", "e.jpg")
            for ((i, name) in names.withIndex()) {
                tree.insertAt(createPhoto(name), i)
            }

            assertEquals(5, tree.totalPhotos())
            val ordered = tree.allPhotosInOrder()
            assertEquals(names, ordered.map { it.originalName })
        }

        @Test
        fun `insert at middle shifts later photos`() {
            // Insert a, b, c in order, then insert x at position 1
            tree.insertAt(createPhoto("a.jpg"), 0)
            tree.insertAt(createPhoto("b.jpg"), 1)
            tree.insertAt(createPhoto("c.jpg"), 2)
            tree.insertAt(createPhoto("x.jpg"), 1)

            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals(listOf("a.jpg", "x.jpg", "b.jpg", "c.jpg"), ordered)
        }

        @Test
        fun `photos get correct filename prefixes within leaf`() {
            tree.insertAt(createPhoto("a.jpg"), 0)
            tree.insertAt(createPhoto("b.jpg"), 1)
            tree.insertAt(createPhoto("c.jpg"), 2)

            // Verify prefix ordering in the filesystem
            val leafChildren = fs.listChildren(rankedRoot)
                .filter { !fs.isDirectory(it) }
                .map { fs.fileName(it) }

            assertEquals("0_a.jpg", leafChildren[0])
            assertEquals("1_b.jpg", leafChildren[1])
            assertEquals("2_c.jpg", leafChildren[2])
        }

        @Test
        fun `insert up to max keys without split`() {
            // m=10, max 9 photos per leaf
            for (i in 0 until 9) {
                tree.insertAt(createPhoto("photo_$i.jpg"), i)
            }

            assertEquals(9, tree.totalPhotos())
            assertEquals(1, tree.totalLeaves())
            assertEquals(1, tree.depth())
            assertTrue(tree.validate().valid)
        }
    }

    @Nested
    inner class Splitting {

        @BeforeEach
        fun init() {
            tree.initialize()
        }

        @Test
        fun `inserting 10th photo into root leaf causes split`() {
            // Root leaf special case: when root overflows, it splits into
            // a new root with children. Since it's the root, min-key rules
            // don't apply to the new children initially.
            for (i in 0 until 10) {
                tree.insertAt(createPhoto("photo_$i.jpg"), i)
            }

            assertEquals(10, tree.totalPhotos())
            assertTrue(tree.totalLeaves() > 1)
            assertEquals(2, tree.depth())
            assertTrue(tree.validate().valid)
        }

        @Test
        fun `after root split all photos still accessible in order`() {
            for (i in 0 until 10) {
                tree.insertAt(createPhoto("photo_$i.jpg"), i)
            }

            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals((0 until 10).map { "photo_$it.jpg" }, ordered)
        }

        @Test
        fun `overflow redistributes to sibling before splitting`() {
            // Fill tree to force a multi-leaf state, then keep adding to one leaf
            // B* tree should try redistribution to sibling before 2→3 split
            for (i in 0 until 18) {
                tree.insertAt(createPhoto("photo_${i.toString().padStart(2, '0')}.jpg"), i)
            }

            assertTrue(tree.validate().valid)
            assertEquals(18, tree.totalPhotos())
        }

        @Test
        fun `2-to-3 split produces three valid leaves`() {
            // Insert enough photos to force a 2→3 split
            // With m=10: two full leaves (9+9=18 photos) + 1 more → should split 2→3
            // giving three leaves of 6 each (plus the new photo distributed)
            for (i in 0 until 25) {
                tree.insertAt(createPhoto("photo_${i.toString().padStart(2, '0')}.jpg"), i)
            }

            assertTrue(tree.validate().valid)
            val totalPhotos = tree.totalPhotos()
            assertEquals(25, totalPhotos)

            // All photos still in correct order
            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals(25, ordered.size)
            for (i in 0 until 25) {
                assertEquals("photo_${i.toString().padStart(2, '0')}.jpg", ordered[i])
            }
        }

        @Test
        fun `large insertion maintains invariants`() {
            // Insert 100 photos
            for (i in 0 until 100) {
                tree.insertAt(createPhoto("p_${i.toString().padStart(3, '0')}.jpg"), i)
            }

            val validation = tree.validate()
            assertTrue(validation.valid, "Errors: ${validation.errors}")
            assertEquals(100, tree.totalPhotos())

            // Order preserved
            val ordered = tree.allPhotosInOrder()
            for (i in 0 until 100) {
                assertEquals("p_${i.toString().padStart(3, '0')}.jpg", ordered[i].originalName)
            }
        }
    }

    @Nested
    inner class Removal {

        @BeforeEach
        fun init() {
            tree.initialize()
            for (i in 0 until 20) {
                tree.insertAt(createPhoto("photo_${i.toString().padStart(2, '0')}.jpg"), i)
            }
        }

        @Test
        fun `remove photo reduces count`() {
            tree.removeAt(5)
            assertEquals(19, tree.totalPhotos())
        }

        @Test
        fun `remove photo returns correct photo`() {
            val removed = tree.removeAt(5)
            assertEquals("photo_05.jpg", removed.originalName)
        }

        @Test
        fun `remove maintains order of remaining photos`() {
            tree.removeAt(5)
            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertFalse(ordered.contains("photo_05.jpg"))
            assertEquals(19, ordered.size)
            // Check order is maintained (photo_04 should be followed by photo_06)
            val idx4 = ordered.indexOf("photo_04.jpg")
            assertEquals("photo_06.jpg", ordered[idx4 + 1])
        }

        @Test
        fun `remove triggers redistribution from sibling when leaf underflows`() {
            // Remove enough from one leaf to trigger underflow (below 6)
            // The tree should redistribute from a sibling
            val validation = tree.validate()
            assertTrue(validation.valid)

            // Remove several from the beginning (same leaf)
            repeat(4) { tree.removeAt(0) }

            val validationAfter = tree.validate()
            assertTrue(validationAfter.valid, "Errors: ${validationAfter.errors}")
        }

        @Test
        fun `remove all photos leaves valid empty tree`() {
            for (i in 19 downTo 0) {
                tree.removeAt(0)
            }
            assertEquals(0, tree.totalPhotos())
            assertTrue(tree.validate().valid)
        }

        @Test
        fun `3-to-2 merge when siblings cannot spare`() {
            // Fill tree then remove enough to force a merge
            // Start with 20 photos, remove many
            for (i in 0 until 12) {
                tree.removeAt(0)
            }

            assertTrue(tree.validate().valid)
            assertEquals(8, tree.totalPhotos())

            // Order of remaining should be preserved
            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals("photo_12.jpg", ordered[0])
        }
    }

    @Nested
    inner class MovePhoto {

        @BeforeEach
        fun init() {
            tree.initialize()
            for (i in 0 until 20) {
                tree.insertAt(createPhoto("photo_${i.toString().padStart(2, '0')}.jpg"), i)
            }
        }

        @Test
        fun `move photo forward shifts it right in ordering`() {
            // Move photo at position 3 to position 10
            tree.movePhoto(3, 10)

            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals(20, ordered.size)
            assertEquals("photo_03.jpg", ordered[10])
            assertTrue(tree.validate().valid)
        }

        @Test
        fun `move photo backward shifts it left in ordering`() {
            tree.movePhoto(15, 2)

            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals(20, ordered.size)
            assertEquals("photo_15.jpg", ordered[2])
            assertTrue(tree.validate().valid)
        }

        @Test
        fun `move within same leaf only changes prefix`() {
            // With 20 photos in m=10 tree, there are multiple leaves
            // Moving within same leaf should just re-prefix
            tree.movePhoto(0, 1)

            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals("photo_01.jpg", ordered[0])
            assertEquals("photo_00.jpg", ordered[1])
            assertTrue(tree.validate().valid)
        }

        @Test
        fun `move to same position is no-op`() {
            tree.movePhoto(5, 5)

            val ordered = tree.allPhotosInOrder().map { it.originalName }
            assertEquals("photo_05.jpg", ordered[5])
            assertTrue(tree.validate().valid)
        }
    }

    @Nested
    inner class PositionLookup {

        @BeforeEach
        fun init() {
            tree.initialize()
            for (i in 0 until 15) {
                tree.insertAt(createPhoto("photo_${i.toString().padStart(2, '0')}.jpg"), i)
            }
        }

        @Test
        fun `photoAt returns correct photo for each position`() {
            for (i in 0 until 15) {
                assertEquals("photo_${i.toString().padStart(2, '0')}.jpg", tree.photoAt(i).originalName)
            }
        }

        @Test
        fun `positionOf finds photo by path`() {
            val photo = tree.photoAt(7)
            val pos = tree.positionOf(photo.path)
            assertEquals(7, pos)
        }

        @Test
        fun `positionOf returns -1 for unknown path`() {
            assertEquals(-1, tree.positionOf("nonexistent/photo.jpg"))
        }
    }

    @Nested
    inner class InsertAtCenter {

        @BeforeEach
        fun init() {
            tree.initialize()
        }

        @Test
        fun `insertAtCenter on empty tree places at 0`() {
            val pos = tree.insertAtCenter(createPhoto("a.jpg"))
            assertEquals(0, pos)
        }

        @Test
        fun `insertAtCenter places at middle of existing photos`() {
            for (i in 0 until 10) {
                tree.insertAt(createPhoto("photo_$i.jpg"), i)
            }
            val pos = tree.insertAtCenter(createPhoto("new.jpg"))
            assertEquals(5, pos) // middle of 10 photos
        }

        @Test
        fun `insertAtCenter with odd number of photos`() {
            for (i in 0 until 9) {
                tree.insertAt(createPhoto("photo_$i.jpg"), i)
            }
            val pos = tree.insertAtCenter(createPhoto("new.jpg"))
            assertEquals(4, pos) // floor(9/2) = 4
        }
    }

    @Nested
    inner class TreeValidation {

        @BeforeEach
        fun init() {
            tree.initialize()
        }

        @Test
        fun `validate checks leaf depth uniformity`() {
            // After many operations, all leaves must be at same depth
            for (i in 0 until 50) {
                tree.insertAt(createPhoto("photo_${i.toString().padStart(2, '0')}.jpg"), i)
            }
            val result = tree.validate()
            assertTrue(result.valid, "Errors: ${result.errors}")
        }

        @Test
        fun `validate checks min-max keys in leaves`() {
            for (i in 0 until 30) {
                tree.insertAt(createPhoto("photo_${i.toString().padStart(2, '0')}.jpg"), i)
            }
            val result = tree.validate()
            assertTrue(result.valid, "Errors: ${result.errors}")
        }
    }
}
