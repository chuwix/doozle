package com.goholand.doozle.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Pair selector: decides which two photos to present for comparison.
 *
 * Rules:
 * 1. If unseen photos exist: pick one unseen + the photo at center of ranked tree
 * 2. If all ranked:
 *    - 70% chance: pick two nearby photos (within a small position window)
 *    - 30% chance: pick two random photos from anywhere
 * 3. Never pick the same photo twice in a pair
 */
class PairSelectorTest {

    private lateinit var fs: InMemoryFileSystem
    private lateinit var tree: BStarTree
    private lateinit var selector: PairSelector
    private val config = EngineConfig(order = 10, neighborRatio = 0.7)
    private val rankedRoot = "project/_ranked"
    private val unseenRoot = "project/_unseen"

    @BeforeEach
    fun setup() {
        fs = InMemoryFileSystem()
        tree = BStarTree(fs, rankedRoot, config)
        tree.initialize()
        fs.createDirectory(unseenRoot)
        selector = PairSelector(tree, fs, unseenRoot, config)
    }

    private fun createUnseenPhoto(name: String): String {
        val path = "$unseenRoot/$name"
        fs.createFile(path)
        return path
    }

    private fun fillRanked(count: Int) {
        for (i in 0 until count) {
            val path = "project/_unseen/ranked_${i.toString().padStart(3, '0')}.jpg"
            fs.createFile(path)
            tree.insertAt(path, i)
        }
    }

    @Nested
    inner class UnseenPriority {

        @Test
        fun `returns unseen photo when available`() {
            fillRanked(10)
            createUnseenPhoto("new_photo.jpg")

            val pair = selector.selectPair()

            assertNotNull(pair)
            assertTrue(
                pair!!.first.isUnseen || pair.second.isUnseen,
                "One photo in pair should be unseen"
            )
        }

        @Test
        fun `unseen photo is paired with ranked photo`() {
            fillRanked(10)
            createUnseenPhoto("new_photo.jpg")

            val pair = selector.selectPair()!!

            val unseen = if (pair.first.isUnseen) pair.first else pair.second
            val ranked = if (pair.first.isUnseen) pair.second else pair.first

            assertTrue(unseen.isUnseen)
            assertFalse(ranked.isUnseen)
        }

        @Test
        fun `returns null when no photos available at all`() {
            val pair = selector.selectPair()
            assertNull(pair)
        }

        @Test
        fun `returns null when only one photo total`() {
            fillRanked(1)
            // Need at least 2 photos to form a pair
            // With 1 ranked and 0 unseen, we can't make a pair
            val pair = selector.selectPair()
            assertNull(pair)
        }

        @Test
        fun `can pair two unseen when no ranked exist`() {
            createUnseenPhoto("a.jpg")
            createUnseenPhoto("b.jpg")

            val pair = selector.selectPair()

            assertNotNull(pair)
            assertTrue(pair!!.first.isUnseen && pair.second.isUnseen)
        }
    }

    @Nested
    inner class RankedPairSelection {

        @BeforeEach
        fun fill() {
            fillRanked(30)
        }

        @Test
        fun `selects two different photos`() {
            val pair = selector.selectPair()!!
            assertNotEquals(pair.first.position, pair.second.position)
        }

        @Test
        fun `with fixed seed, neighbor ratio produces expected distribution`() {
            // Run many selections and verify approximately 70% are nearby pairs
            val selectorSeeded = PairSelector(tree, fs, unseenRoot, config, seed = 42L)

            var nearbyCount = 0
            val total = 100
            val maxNeighborDistance = 5 // "nearby" threshold

            repeat(total) {
                val pair = selectorSeeded.selectPair()!!
                val distance = kotlin.math.abs(pair.first.position - pair.second.position)
                if (distance <= maxNeighborDistance) {
                    nearbyCount++
                }
            }

            // Allow some tolerance: expect 55-85% nearby (70% ± 15%)
            assertTrue(nearbyCount in 55..85,
                "Expected ~70% nearby pairs, got $nearbyCount/$total")
        }

        @Test
        fun `never returns same photo as both sides of pair`() {
            val selectorSeeded = PairSelector(tree, fs, unseenRoot, config, seed = 123L)

            repeat(50) {
                val pair = selectorSeeded.selectPair()!!
                assertNotEquals(pair.first.position, pair.second.position,
                    "Pair should not contain same photo on both sides")
            }
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `works with exactly 2 ranked photos`() {
            fillRanked(2)
            val pair = selector.selectPair()

            assertNotNull(pair)
            val positions = setOf(pair!!.first.position, pair.second.position)
            assertEquals(setOf(0, 1), positions)
        }

        @Test
        fun `one unseen and one ranked forms valid pair`() {
            fillRanked(1)
            createUnseenPhoto("new.jpg")

            val pair = selector.selectPair()
            assertNotNull(pair)
        }

        @Test
        fun `many unseen photos picks one at random`() {
            fillRanked(5)
            for (i in 0 until 20) {
                createUnseenPhoto("unseen_$i.jpg")
            }

            // Run multiple selections to verify randomness (not always same unseen)
            val selectorSeeded = PairSelector(tree, fs, unseenRoot, config, seed = 42L)
            val unseenPicked = mutableSetOf<String>()
            repeat(10) {
                val pair = selectorSeeded.selectPair()!!
                val unseen = if (pair.first.isUnseen) pair.first else pair.second
                unseenPicked.add(unseen.path)
            }

            assertTrue(unseenPicked.size > 1,
                "Should pick different unseen photos across selections")
        }
    }
}
