package com.goholand.doozle.engine

/**
 * In-memory filesystem for testing the B* tree without disk I/O.
 */
class InMemoryFileSystem : FileSystem {
    // Stores directory entries. A directory exists if it's a key here.
    private val directories = mutableSetOf<String>()
    // Stores files. Key = full path, Value = file content (unused, just presence matters)
    private val files = mutableSetOf<String>()

    init {
        directories.add("") // root
    }

    override fun listChildren(path: String): List<String> {
        val normalized = path.trimEnd('/')
        val children = mutableListOf<String>()

        // Find direct children (files and dirs one level below)
        val prefix = if (normalized.isEmpty()) "" else "$normalized/"

        for (dir in directories) {
            if (dir.startsWith(prefix) && dir != normalized) {
                val relative = dir.removePrefix(prefix)
                if (!relative.contains('/')) {
                    children.add(dir)
                }
            }
        }
        for (file in files) {
            if (file.startsWith(prefix)) {
                val relative = file.removePrefix(prefix)
                if (!relative.contains('/')) {
                    children.add(file)
                }
            }
        }

        return children.sorted()
    }

    override fun isDirectory(path: String): Boolean {
        return directories.contains(path.trimEnd('/'))
    }

    override fun exists(path: String): Boolean {
        val normalized = path.trimEnd('/')
        return directories.contains(normalized) || files.contains(normalized)
    }

    override fun createDirectory(path: String) {
        val normalized = path.trimEnd('/')
        // Create all parent directories
        val parts = normalized.split('/')
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current/$part"
            directories.add(current)
        }
    }

    override fun move(src: String, dst: String) {
        val srcNorm = src.trimEnd('/')
        val dstNorm = dst.trimEnd('/')

        if (files.contains(srcNorm)) {
            files.remove(srcNorm)
            files.add(dstNorm)
            // Ensure parent directory of dst exists
            val parentPath = parent(dstNorm)
            if (parentPath.isNotEmpty()) {
                createDirectory(parentPath)
            }
        } else if (directories.contains(srcNorm)) {
            // Move directory and all contents
            val toMove = directories.filter { it == srcNorm || it.startsWith("$srcNorm/") }
            val filesToMove = files.filter { it.startsWith("$srcNorm/") }

            directories.removeAll(toMove.toSet())
            files.removeAll(filesToMove.toSet())

            for (dir in toMove) {
                val newPath = dstNorm + dir.removePrefix(srcNorm)
                directories.add(newPath)
            }
            for (file in filesToMove) {
                val newPath = dstNorm + file.removePrefix(srcNorm)
                files.add(newPath)
            }
        }
    }

    override fun rename(src: String, dst: String) {
        move(src, dst)
    }

    override fun delete(path: String) {
        val normalized = path.trimEnd('/')
        // Remove the path itself
        files.remove(normalized)
        directories.remove(normalized)
        // Recursively remove all contents if it was a directory
        files.removeAll { it.startsWith("$normalized/") }
        directories.removeAll { it.startsWith("$normalized/") }
    }

    /** Helper: create a file (for testing - simulates a photo existing). */
    override fun createFile(path: String) {
        val normalized = path.trimEnd('/')
        files.add(normalized)
        // Ensure parent exists
        val parentPath = parent(normalized)
        if (parentPath.isNotEmpty()) {
            createDirectory(parentPath)
        }
    }

    /** Helper: check if something is a file (not directory). */
    fun isFile(path: String): Boolean = files.contains(path.trimEnd('/'))

    /** Debug: dump all contents. */
    fun dump(): String {
        val sb = StringBuilder()
        sb.appendLine("Directories:")
        directories.sorted().forEach { sb.appendLine("  $it/") }
        sb.appendLine("Files:")
        files.sorted().forEach { sb.appendLine("  $it") }
        return sb.toString()
    }
}
