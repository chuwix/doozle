package com.goholand.doozle.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * FileSystem implementation backed by Android's SAF (Storage Access Framework).
 * Uses DocumentFile for all operations to work with user-selected folders.
 *
 * Caches directory/file status from listChildren to avoid expensive re-lookups.
 */
class SafFileSystem(
    private val context: Context,
    private val rootUri: Uri
) : FileSystem {

    companion object {
        private const val TAG = "SafFileSystem"
    }

    // Cache: path -> isDirectory. Populated during listChildren.
    private val dirCache = mutableMapOf<String, Boolean>()

    private fun resolve(path: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: run {
            Log.e(TAG, "resolve: fromTreeUri returned null for $rootUri")
            return null
        }
        if (path.isEmpty()) return root
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current: DocumentFile = root
        for (part in parts) {
            current = current.findFile(part) ?: run {
                Log.d(TAG, "resolve: findFile('$part') returned null in path '$path'")
                return null
            }
        }
        return current
    }

    override fun listChildren(path: String): List<String> {
        val dir = resolve(path) ?: return emptyList()
        val files = dir.listFiles()
        Log.d(TAG, "listChildren('$path'): ${files.size} children, uri=${dir.uri}, canRead=${dir.canRead()}")
        return files.map { child ->
            val childPath = if (path.isEmpty()) child.name ?: "" else "$path/${child.name ?: ""}"
            // Cache directory status from the DocumentFile we already have
            dirCache[childPath] = child.isDirectory
            childPath
        }
    }

    override fun isDirectory(path: String): Boolean {
        // Use cache if available (populated by listChildren)
        dirCache[path]?.let { return it }
        return resolve(path)?.isDirectory == true
    }

    override fun exists(path: String): Boolean {
        if (dirCache.containsKey(path)) return true
        return resolve(path) != null
    }

    override fun createDirectory(path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return
        var current = root
        for (part in parts) {
            val existing = current.findFile(part)
            current = if (existing != null && existing.isDirectory) {
                existing
            } else {
                current.createDirectory(part) ?: run {
                    Log.e(TAG, "createDirectory: failed to create '$part' in '$path'")
                    return
                }
            }
        }
        dirCache[path] = true
    }

    override fun move(src: String, dst: String) {
        val srcDoc = resolve(src) ?: run {
            Log.e(TAG, "move: source '$src' not found")
            return
        }
        val dstParentPath = dst.substringBeforeLast('/', "")
        val dstName = dst.substringAfterLast('/')

        Log.d(TAG, "move: '$src' -> '$dst' (isDir=${srcDoc.isDirectory})")

        if (srcDoc.isDirectory) {
            srcDoc.renameTo(dstName)
        } else {
            val dstParent = if (dstParentPath.isEmpty()) {
                DocumentFile.fromTreeUri(context, rootUri)
            } else {
                resolve(dstParentPath)
            } ?: run {
                Log.e(TAG, "move: dst parent '$dstParentPath' not found")
                return
            }

            val mimeType = context.contentResolver.getType(srcDoc.uri) ?: "application/octet-stream"
            val newFile = dstParent.createFile(mimeType, dstName) ?: run {
                Log.e(TAG, "move: createFile('$dstName') in '$dstParentPath' failed")
                return
            }

            context.contentResolver.openInputStream(srcDoc.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            srcDoc.delete()
        }
        // Invalidate cache for moved paths
        dirCache.remove(src)
    }

    override fun rename(src: String, dst: String) {
        val srcDoc = resolve(src) ?: return
        val newName = dst.substringAfterLast('/')
        srcDoc.renameTo(newName)
        dirCache[dst] = dirCache.remove(src) ?: false
    }

    override fun delete(path: String) {
        resolve(path)?.delete()
        dirCache.remove(path)
    }

    override fun createFile(path: String) {
        val parentPath = path.substringBeforeLast('/', "")
        val fileName = path.substringAfterLast('/')
        createDirectory(parentPath)
        val parent = if (parentPath.isEmpty()) {
            DocumentFile.fromTreeUri(context, rootUri)
        } else {
            resolve(parentPath)
        } ?: return
        parent.createFile("application/octet-stream", fileName)
        dirCache[path] = false
    }
}
