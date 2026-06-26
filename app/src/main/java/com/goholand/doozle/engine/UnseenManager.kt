package com.goholand.doozle.engine

/**
 * Manages the _unseen folder for a project.
 *
 * Flow:
 * 1. On project open: scanForNewPhotos() moves new images from project root to _unseen/
 * 2. PairSelector prioritizes unseen photos for comparison
 * 3. After first comparison: promotePhoto() moves from _unseen/ to a staging path
 *    for BStarTree.insertAtCenter()
 */
class UnseenManager(
    private val fs: FileSystem,
    private val projectRoot: String
) {
    companion object {
        private const val UNSEEN_DIR = "_unseen"
        private const val RANKED_DIR = "_ranked"

        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif"
        )

        fun isImageFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in IMAGE_EXTENSIONS
        }
    }

    val unseenPath get() = "$projectRoot/$UNSEEN_DIR"
    val rankedPath get() = "$projectRoot/$RANKED_DIR"

    fun initialize() {
        if (!fs.exists(unseenPath)) {
            fs.createDirectory(unseenPath)
        }
        if (!fs.exists(rankedPath)) {
            fs.createDirectory(rankedPath)
        }
    }

    /**
     * Scan project folder recursively for new image files and move them to _unseen/.
     * Skips _unseen/ and _ranked/ directories. All nested images are collected.
     * Returns the number of photos moved.
     */
    fun scanForNewPhotos(): Int {
        var count = 0
        scanDirectory(projectRoot) { child, name ->
            // Move to _unseen, handling name collisions
            val targetPath = "$unseenPath/$name"
            if (fs.exists(targetPath)) {
                // Collision: add a suffix
                val baseName = name.substringBeforeLast('.')
                val ext = name.substringAfterLast('.', "")
                var suffix = 1
                var resolved = "$unseenPath/${baseName}_$suffix.$ext"
                while (fs.exists(resolved)) {
                    suffix++
                    resolved = "$unseenPath/${baseName}_$suffix.$ext"
                }
                fs.move(child, resolved)
            } else {
                fs.move(child, targetPath)
            }
            count++
        }
        return count
    }

    /**
     * Recursively walk a directory, invoking [onImage] for each image file found.
     * Skips _unseen/, _ranked/, and hidden directories/files.
     */
    private fun scanDirectory(dirPath: String, onImage: (path: String, name: String) -> Unit) {
        val children = fs.listChildren(dirPath)
        for (child in children) {
            val name = fs.fileName(child)
            if (name.startsWith(".")) continue

            if (fs.isDirectory(child)) {
                // Skip our managed directories
                if (name == UNSEEN_DIR || name == RANKED_DIR) continue
                // Recurse into subdirectories
                scanDirectory(child, onImage)
            } else {
                if (isImageFile(name)) {
                    onImage(child, name)
                }
            }
        }
    }

    /**
     * Get all photos currently in _unseen/.
     */
    fun getUnseenPhotos(): List<Photo> {
        if (!fs.exists(unseenPath)) return emptyList()
        return fs.listChildren(unseenPath)
            .filter { !fs.isDirectory(it) && !fs.fileName(it).startsWith(".") }
            .map { Photo(originalName = fs.fileName(it), path = it) }
    }

    /**
     * Whether there are any unseen photos.
     */
    fun hasUnseen(): Boolean {
        if (!fs.exists(unseenPath)) return false
        return fs.listChildren(unseenPath)
            .any { !fs.isDirectory(it) && !fs.fileName(it).startsWith(".") }
    }

    /**
     * Move a photo out of _unseen/ to a staging path for tree insertion.
     * Returns the new path of the file (caller should pass this to BStarTree.insertAt).
     */
    fun promotePhoto(photo: Photo): String {
        val stagingPath = "$projectRoot/.staging_${photo.originalName}"
        fs.move(photo.path, stagingPath)
        return stagingPath
    }
}
