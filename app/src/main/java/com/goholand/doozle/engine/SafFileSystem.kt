package com.goholand.doozle.engine

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * FileSystem implementation backed by Android's SAF (Storage Access Framework).
 * Uses DocumentFile for all operations to work with user-selected folders.
 */
class SafFileSystem(
    private val context: Context,
    private val rootUri: Uri
) : FileSystem {

    private fun resolve(path: String): DocumentFile? {
        if (path.isEmpty()) return DocumentFile.fromTreeUri(context, rootUri)
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = root
        for (part in parts) {
            current = current.findFile(part) ?: return null
        }
        return current
    }

    override fun listChildren(path: String): List<String> {
        val dir = resolve(path) ?: return emptyList()
        return dir.listFiles().map { child ->
            if (path.isEmpty()) child.name ?: "" else "$path/${child.name ?: ""}"
        }
    }

    override fun isDirectory(path: String): Boolean {
        return resolve(path)?.isDirectory == true
    }

    override fun exists(path: String): Boolean {
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
                current.createDirectory(part) ?: return
            }
        }
    }

    override fun move(src: String, dst: String) {
        // SAF doesn't have a native move operation for all cases.
        // For files: copy content + delete source. For directories: rename if same parent.
        val srcDoc = resolve(src) ?: return
        val dstParentPath = dst.substringBeforeLast('/', "")
        val dstName = dst.substringAfterLast('/')

        if (srcDoc.isDirectory) {
            // For directories, try rename if same parent
            srcDoc.renameTo(dstName)
        } else {
            // For files: create in target, copy content, delete source
            val dstParent = if (dstParentPath.isEmpty()) {
                DocumentFile.fromTreeUri(context, rootUri)
            } else {
                resolve(dstParentPath)
            } ?: return

            val mimeType = context.contentResolver.getType(srcDoc.uri) ?: "application/octet-stream"
            val newFile = dstParent.createFile(mimeType, dstName) ?: return

            context.contentResolver.openInputStream(srcDoc.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            srcDoc.delete()
        }
    }

    override fun rename(src: String, dst: String) {
        val srcDoc = resolve(src) ?: return
        val newName = dst.substringAfterLast('/')
        srcDoc.renameTo(newName)
    }

    override fun delete(path: String) {
        resolve(path)?.delete()
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
    }
}
