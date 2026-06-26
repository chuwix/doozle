package com.goholand.doozle.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NudgeAlgorithmTest {

    private lateinit var fs: InMemoryFileSystem
    private lateinit var tree: BStarTree
    private val config = EngineConfig(order = 10, nudgeDivisor = 20, unseenBoostMultiplier = 2)
    private val rankedRoot = "project/_ranked"

    @BeforeEach
    fun setup() {
        fs = InMemoryFileSystem()
        tree = BStarTree(fs, rankedRoot, config)
        tree.initialize()
    }

    private fun createPhoto(name: String): String {
        val path = "project/_unseen/$name"
        fs.createFile(path)
        return path
    }

    private fun fillTree(count: Int) {
        for (i in 0 until count) {
            tree.insertAt(createPhoto("photo_${i.toString().padStart(3, '0')}.jpg"), i)
        }
    }

    @Nested
    inner class NudgeCalculation {

        @Test
        fun `nudge moves winner up and loser down`() {
            fillTree(40) // 40 photos, delta = max(1, 40/20/2) = 1

            val (newWinner, newLoser) = tree.applyComparison(
                winnerPosition = 10,
                loserPosition = 20
            )

            assertTrue(newWinner > 10, "Winner should move up, got $newWinner")
            assertTrue(newLoser < 20, "Loser should move down, got $newLoser")
        }

        @Test
        fun `nudge delta is at least 1 position`() {
            fillTree(10) // small tree: 10/20/2 = 0.25, should clamp to 1

            val (newWinner, newLoser) = tree.applyComparison(
                winnerPosition = 3,
                loserPosition = 7
            )

            assertTrue(newWinner >= 4, "Winner should move at least 1 position up")
            assertTrue(newLoser <= 6, "Loser should move at least 1 position down")
        }

        @Test
        fun `nudge with larger tree produces proportional movement`() {
            fillTree(100) // delta = max(1, 100/20/2) = 2 (rounding)

            val (newWinner, newLoser) = tree.applyComparison(
                winnerPosition = 50,
                loserPosition = 60
            )

            // With 100 photos and x=20: delta = 100/20/2 = 2 (rounded)
            val expectedDelta = maxOf(1, 100 / config.nudgeDivisor / 2)
            assertEquals(50 + expectedDelta, newWinner)
            assertEquals(60 - expectedDelta, newLoser)
        }

        @Test
        fun `winner cannot exceed max position`() {
            fillTree(20)

            val (newWinner, _) = tree.applyComparison(
                winnerPosition = 19, // already at top
                loserPosition = 10
            )

            assertEquals(19, newWinner) // clamped to max
        }

        @Test
        fun `loser cannot go below position 0`() {
            fillTree(20)

            val (_, newLoser) = tree.applyComparison(
                winnerPosition = 10,
                loserPosition = 0 // already at bottom
            )

            assertEquals(0, newLoser) // clamped to 0
        }

        @Test
        fun `photos do not cross each other after nudge`() {
            fillTree(40)

            // Winner at 15, loser at 16 (adjacent) - after nudge they shouldn't cross
            val (newWinner, newLoser) = tree.applyComparison(
                winnerPosition = 15,
                loserPosition = 16
            )

            // Winner moves right, loser moves left - they might end up at same spot or cross
            // The implementation should handle this (e.g., just swap or minimal movement)
            assertTrue(newWinner >= newLoser || newWinner == newLoser,
                "Winner ($newWinner) should not end up below loser ($newLoser) unless they meet")
        }
    }

    @Nested
    inner class UnseenBoost {

        @Test
        fun `unseen winner gets boosted movement`() {
            fillTree(100) // delta = 100/20/2 = 2

            val (newWinnerBoosted, _) = tree.applyComparison(
                winnerPosition = 50,
                loserPosition = 60,
                winnerIsUnseen = true
            )

            val (newWinnerNormal, _) = tree.applyComparison(
                winnerPosition = 50,
                loserPosition = 60,
                winnerIsUnseen = false
            )

            // Boosted winner should move further than normal winner
            assertTrue(newWinnerBoosted > newWinnerNormal,
                "Boosted ($newWinnerBoosted) should exceed normal ($newWinnerNormal)")
        }

        @Test
        fun `unseen loser gets boosted downward movement`() {
            fillTree(100)

            val (_, newLoserBoosted) = tree.applyComparison(
                winnerPosition = 50,
                loserPosition = 60,
                loserIsUnseen = true
            )

            val (_, newLoserNormal) = tree.applyComparison(
                winnerPosition = 50,
                loserPosition = 60,
                loserIsUnseen = false
            )

            // Boosted loser should move further down
            assertTrue(newLoserBoosted < newLoserNormal,
                "Boosted loser ($newLoserBoosted) should be lower than normal ($newLoserNormal)")
        }

        @Test
        fun `boost multiplier is applied correctly`() {
            fillTree(100) // delta_base = 100/20/2 = 2, boosted = 2*2 = 4

            val (newWinner, _) = tree.applyComparison(
                winnerPosition = 50,
                loserPosition = 60,
                winnerIsUnseen = true
            )

            val expectedDelta = maxOf(1, 100 / config.nudgeDivisor / 2)
            val boostedDelta = expectedDelta * config.unseenBoostMultiplier
            assertEquals(50 + boostedDelta, newWinner)
        }

        @Test
        fun `non-unseen photo in comparison with unseen gets normal movement`() {
            fillTree(100)

            // Winner is unseen, loser is normal
            val (_, newLoser) = tree.applyComparison(
                winnerPosition = 50,
                loserPosition = 60,
                winnerIsUnseen = true,
                loserIsUnseen = false
            )

            val expectedDelta = maxOf(1, 100 / config.nudgeDivisor / 2)
            assertEquals(60 - expectedDelta, newLoser) // normal delta for loser
        }

        @Test
        fun `boost is clamped to valid range`() {
            fillTree(100)

            // Unseen winner near the top - boost would exceed max
            val (newWinner, _) = tree.applyComparison(
                winnerPosition = 98,
                loserPosition = 50,
                winnerIsUnseen = true
            )

            assertEquals(99, newWinner) // clamped to max (totalPhotos - 1)
        }
    }

    @Nested
    inner class NudgeIntegration {

        @Test
        fun `comparison physically moves photos in tree`() {
            fillTree(20)

            val winnerName = tree.photoAt(5).originalName
            val loserName = tree.photoAt(15).originalName

            tree.applyComparison(winnerPosition = 5, loserPosition = 15)

            // Winner should now be at a higher position
            val allPhotos = tree.allPhotosInOrder().map { it.originalName }
            val newWinnerPos = allPhotos.indexOf(winnerName)
            val newLoserPos = allPhotos.indexOf(loserName)

            assertTrue(newWinnerPos > 5, "Winner should have moved up from 5 to $newWinnerPos")
            assertTrue(newLoserPos < 15, "Loser should have moved down from 15 to $newLoserPos")
            assertTrue(tree.validate().valid)
        }

        @Test
        fun `multiple comparisons converge ordering`() {
            fillTree(20)

            // Repeatedly declare photo_15 as winner over photo_05
            // Eventually photo_15 should be ranked higher than photo_05
            repeat(10) {
                val pos15 = tree.allPhotosInOrder().indexOfFirst { it.originalName == "photo_015.jpg" }
                val pos05 = tree.allPhotosInOrder().indexOfFirst { it.originalName == "photo_005.jpg" }
                if (pos15 < pos05) {
                    tree.applyComparison(winnerPosition = pos15, loserPosition = pos05)
                }
            }

            val final15 = tree.allPhotosInOrder().indexOfFirst { it.originalName == "photo_015.jpg" }
            val final05 = tree.allPhotosInOrder().indexOfFirst { it.originalName == "photo_005.jpg" }
            assertTrue(final15 > final05,
                "After repeated wins, photo_015 ($final15) should rank above photo_005 ($final05)")
            assertTrue(tree.validate().valid)
        }

        @Test
        fun `tree remains valid after many random comparisons`() {
            fillTree(50)

            val rng = java.util.Random(42)
            repeat(100) {
                val a = rng.nextInt(tree.totalPhotos())
                var b = rng.nextInt(tree.totalPhotos())
                while (b == a) b = rng.nextInt(tree.totalPhotos())
                tree.applyComparison(
                    winnerPosition = maxOf(a, b),
                    loserPosition = minOf(a, b)
                )
            }

            assertTrue(tree.validate().valid)
            assertEquals(50, tree.totalPhotos())
        }
    }
}
