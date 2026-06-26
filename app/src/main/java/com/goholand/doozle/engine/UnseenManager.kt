package com.goholand.doozle.engine

/**
 * Manages unseen photos for a project.
 *
 * "Unseen" = any image file in the project folder (recursively) that is NOT inside _ranked/.
 * New photos are simply dumped into the folder; no separate tracking directory needed.
 *
 * Flow:
 * 1. PairSelector picks a random unseen photo for comparison
 * 2. After first comparison: promotePhoto() moves the file to staging for tree insertion
 */
class UnseenManager(
    private val fs: FileSystem,
    private val projectRoot: String
) {
    companion object {
        const val RANKED_DIR = "_ranked"

        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif"
        )

        fun isImageFile(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in IMAGE_EXTENSIONS
        }
    }

    val rankedPath get() = if (projectRoot.isEmpty()) RANKED_DIR else "$projectRoot/$RANKED_DIR"

    fun initialize() {
        if (!fs.exists(rankedPath)) {
            fs.createDirectory(rankedPath)
        }
    }

    /**
     * Get all unseen photos: any image in the project folder (recursive) NOT inside _ranked/.
     */
    fun getUnseenPhotos(): List<Photo> {
        val result = mutableListOf<Photo>()
        scanDirectory(projectRoot) { path, name ->
            result.add(Photo(originalName = name, path = path))
        }
        return result
    }

    /**
     * Whether there are any unseen photos.
     */
    fun hasUnseen(): Boolean {
        return scanHasImage(projectRoot)
    }

    /**
     * Move a photo from its current location to a staging path for tree insertion.
     * Returns the new path of the file (caller should pass this to BStarTree.insertAt).
     */
    fun promotePhoto(photo: Photo): String {
        val prefix = if (projectRoot.isEmpty()) "" else "$projectRoot/"
        val stagingPath = "${prefix}.staging_${photo.originalName}"
        fs.move(photo.path, stagingPath)
        return stagingPath
    }

    private fun scanHasImage(dirPath: String): Boolean {
        val children = fs.listChildren(dirPath)
        for (child in children) {
            val name = fs.fileName(child)
            if (name.startsWith(".")) continue

            if (fs.isDirectory(child)) {
                if (name == RANKED_DIR) continue
                if (scanHasImage(child)) return true
            } else {
                if (isImageFile(name)) return true
            }
        }
        return false
    }

    private fun scanDirectory(dirPath: String, onImage: (path: String, name: String) -> Unit) {
        val children = fs.listChildren(dirPath)
        for (child in children) {
            val name = fs.fileName(child)
            if (name.startsWith(".")) continue

            if (fs.isDirectory(child)) {
                if (name == RANKED_DIR) continue
                scanDirectory(child, onImage)
            } else {
                if (isImageFile(name)) {
                    onImage(child, name)
                }
            }
        }
    }
}
