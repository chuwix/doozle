package com.goholand.doozle.engine

/**
 * Abstraction over filesystem operations so the B* tree engine
 * can be tested with an in-memory implementation.
 */
interface FileSystem {
    /** List direct children of a directory (files and subdirectories). */
    fun listChildren(path: String): List<String>

    /** Check if a path is a directory. */
    fun isDirectory(path: String): Boolean

    /** Check if a path exists. */
    fun exists(path: String): Boolean

    /** Create a directory (and parents if needed). */
    fun createDirectory(path: String)

    /** Move a file or directory from src to dst. */
    fun move(src: String, dst: String)

    /** Rename a file (within same parent). */
    fun rename(src: String, dst: String)

    /** Delete a file or empty directory. */
    fun delete(path: String)

    /** Create an empty file (for internal operations like move-via-recreate). */
    fun createFile(path: String)

    /** Get the file name (last path component) from a full path. */
    fun fileName(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /** Get the parent directory path. */
    fun parent(path: String): String = path.trimEnd('/').substringBeforeLast('/')
}
