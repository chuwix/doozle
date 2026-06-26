package com.goholand.doozle.engine

import java.util.Random

/**
 * Represents one side of a comparison pair.
 */
data class PairCandidate(
    val path: String,
    val position: Int,   // -1 if unseen (not yet in tree)
    val isUnseen: Boolean
)

/**
 * Selects pairs of photos for comparison.
 *
 * Rules:
 * 1. If unseen photos exist: pick one unseen + the photo at center of ranked tree
 * 2. If all ranked:
 *    - neighborRatio chance: pick two nearby photos (within a small position window)
 *    - (1 - neighborRatio) chance: pick two random photos from anywhere
 * 3. Never pick the same photo twice in a pair
 */
class PairSelector(
    private val tree: BStarTree,
    private val fs: FileSystem,
    private val unseenRoot: String,
    private val config: EngineConfig,
    private val seed: Long? = null
) {
    private val rng: Random = if (seed != null) Random(seed) else Random()

    fun selectPair(): Pair<PairCandidate, PairCandidate>? {
        val unseenPhotos = listUnseen()
        val rankedCount = tree.totalPhotos()

        // Not enough photos to form a pair
        if (unseenPhotos.size + rankedCount < 2) return null

        // Case 1: Unseen photos exist
        if (unseenPhotos.isNotEmpty()) {
            val unseen = unseenPhotos[rng.nextInt(unseenPhotos.size)]
            val unseenCandidate = PairCandidate(path = unseen, position = -1, isUnseen = true)

            if (rankedCount > 0) {
                // Pair with a ranked photo (from center area)
                val centerPos = rankedCount / 2
                val rankedPhoto = tree.photoAt(centerPos)
                val rankedCandidate = PairCandidate(
                    path = rankedPhoto.path,
                    position = centerPos,
                    isUnseen = false
                )
                return Pair(unseenCandidate, rankedCandidate)
            } else if (unseenPhotos.size >= 2) {
                // Two unseen photos
                var second = unseenPhotos[rng.nextInt(unseenPhotos.size)]
                while (second == unseen) {
                    second = unseenPhotos[rng.nextInt(unseenPhotos.size)]
                }
                val secondCandidate = PairCandidate(path = second, position = -1, isUnseen = true)
                return Pair(unseenCandidate, secondCandidate)
            }
            return null
        }

        // Case 2: All photos are ranked
        if (rankedCount < 2) return null

        val useNeighbor = rng.nextDouble() < config.neighborRatio

        return if (useNeighbor) {
            selectNeighborPair(rankedCount)
        } else {
            selectRandomPair(rankedCount)
        }
    }

    /**
     * List all unseen photo paths (recursive scan of unseen directory).
     */
    fun listUnseen(): List<String> {
        if (!fs.exists(unseenRoot)) return emptyList()
        return collectFiles(unseenRoot)
    }

    private fun collectFiles(path: String): List<String> {
        val result = mutableListOf<String>()
        for (child in fs.listChildren(path)) {
            if (fs.isDirectory(child)) {
                result.addAll(collectFiles(child))
            } else if (!fs.fileName(child).startsWith(".")) {
                result.add(child)
            }
        }
        return result
    }

    private fun selectNeighborPair(rankedCount: Int): Pair<PairCandidate, PairCandidate> {
        val maxDistance = minOf(5, rankedCount / 2)
        val posA = rng.nextInt(rankedCount)
        val offset = rng.nextInt(maxOf(1, maxDistance)) + 1
        val posB = if (rng.nextBoolean()) {
            minOf(posA + offset, rankedCount - 1)
        } else {
            maxOf(posA - offset, 0)
        }

        val adjustedB = if (posB == posA) {
            if (posA < rankedCount - 1) posA + 1 else posA - 1
        } else posB

        val photoA = tree.photoAt(posA)
        val photoB = tree.photoAt(adjustedB)

        return Pair(
            PairCandidate(path = photoA.path, position = posA, isUnseen = false),
            PairCandidate(path = photoB.path, position = adjustedB, isUnseen = false)
        )
    }

    private fun selectRandomPair(rankedCount: Int): Pair<PairCandidate, PairCandidate> {
        val posA = rng.nextInt(rankedCount)
        var posB = rng.nextInt(rankedCount)
        while (posB == posA) {
            posB = rng.nextInt(rankedCount)
        }

        val photoA = tree.photoAt(posA)
        val photoB = tree.photoAt(posB)

        return Pair(
            PairCandidate(path = photoA.path, position = posA, isUnseen = false),
            PairCandidate(path = photoB.path, position = posB, isUnseen = false)
        )
    }
}
