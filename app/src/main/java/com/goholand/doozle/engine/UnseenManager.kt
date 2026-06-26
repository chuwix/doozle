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
     * Scan project root for new image files and move them to _unseen/.
     * Only direct children of projectRoot are scanned (not subdirectories).
     * Returns the number of photos moved.
     */
    fun scanForNewPhotos(): Int {
        val children = fs.listChildren(projectRoot)
        var count = 0

        for (child in children) {
            // Skip directories (including _unseen, _ranked, and any subdirs)
            if (fs.isDirectory(child)) continue

            val name = fs.fileName(child)
            // Skip hidden files
            if (name.startsWith(".")) continue
            // Skip non-image files
            if (!isImageFile(name)) continue

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
