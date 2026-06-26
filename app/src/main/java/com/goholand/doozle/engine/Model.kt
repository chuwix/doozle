package com.goholand.doozle.engine

/**
 * Represents a photo in the ranking system.
 *
 * @param originalName The original filename without the ordering prefix.
 * @param path The full path to the photo file.
 */
data class Photo(
    val originalName: String,
    val path: String
)

/**
 * Represents the result of a comparison between two photos.
 */
data class ComparisonResult(
    val winner: Photo,
    val loser: Photo,
    val winnerWasUnseen: Boolean
)

/**
 * Configuration for the B* tree ranking engine.
 */
data class EngineConfig(
    val order: Int = 10,                // m=10: max 9 photos per leaf, min 6
    val nudgeDivisor: Int = 20,         // x: nudge = totalPhotos / x / 2
    val unseenBoostMultiplier: Int = 2, // boost for unseen photo's first comparison
    val neighborRatio: Double = 0.7     // probability of picking nearby pair vs random
) {
    val maxKeys: Int get() = order - 1                      // 9
    val minKeys: Int get() = Math.ceil((2.0 * maxKeys) / 3).toInt()  // 6
    val maxChildren: Int get() = order                       // 10
    val minChildren: Int get() = Math.ceil((2.0 * order - 1) / 3).toInt()  // 7
}
