package com.goholand.doozle.engine

/**
 * B* tree-based photo ranking engine.
 *
 * The tree is stored on disk as a folder structure:
 * - Internal nodes are folders containing numbered subfolders (0/ through max 9/)
 * - Leaf nodes are folders containing photos with prefix-ordered filenames (0_name.jpg, 1_name.jpg, ...)
 *
 * Order m=10:
 * - Leaf: min 6, max 9 photos
 * - Internal: min 7, max 10 children
 * - Split: 2 full nodes → 3 nodes with 6 keys each
 * - Merge: 3 sparse nodes → 2 nodes
 */
class BStarTree(
    private val fs: FileSystem,
    private val rankedRoot: String,
    private val config: EngineConfig = EngineConfig()
) {
    companion object {
        private const val PREFIX_SEPARATOR = "_"

        /** Extract the original filename from a prefixed name like "3_sunset.jpg" */
        fun stripPrefix(prefixedName: String): String {
            val idx = prefixedName.indexOf(PREFIX_SEPARATOR)
            if (idx <= 0) return prefixedName
            val prefix = prefixedName.substring(0, idx)
            return if (prefix.toIntOrNull() != null) {
                prefixedName.substring(idx + 1)
            } else {
                prefixedName
            }
        }

        /** Create a prefixed filename like "3_sunset.jpg" */
        fun withPrefix(index: Int, originalName: String): String {
            return "$index$PREFIX_SEPARATOR$originalName"
        }

        /** Extract prefix index from a prefixed filename */
        fun prefixIndex(prefixedName: String): Int {
            val idx = prefixedName.indexOf(PREFIX_SEPARATOR)
            if (idx <= 0) return -1
            return prefixedName.substring(0, idx).toIntOrNull() ?: -1
        }
    }

    // ==================== Public API ====================

    fun initialize() {
        if (!fs.exists(rankedRoot)) {
            fs.createDirectory(rankedRoot)
        }
    }

    fun totalPhotos(): Int = countPhotos(rankedRoot)

    fun totalLeaves(): Int = countLeaves(rankedRoot)

    fun insertAt(photoPath: String, position: Int) {
        val originalName = stripPrefix(fs.fileName(photoPath))
        val (leafPath, localIndex) = findLeafForPosition(rankedRoot, position)
        insertIntoLeaf(leafPath, localIndex, originalName, photoPath)
    }

    fun insertAtCenter(photoPath: String): Int {
        val total = totalPhotos()
        val center = total / 2
        insertAt(photoPath, center)
        return center
    }

    fun removeAt(position: Int): Photo {
        val (leafPath, localIndex) = findLeafForPosition(rankedRoot, position)
        val files = getFilesInLeaf(leafPath)
        val (filePath, origName) = files[localIndex]

        // Remove the file
        fs.delete(filePath)

        // Reprefix the remaining files
        val remaining = files.toMutableList()
        remaining.removeAt(localIndex)
        rewriteLeaf(leafPath, remaining)

        // Handle underflow
        handleUnderflow(leafPath)

        return Photo(originalName = origName, path = filePath)
    }

    fun photoAt(position: Int): Photo {
        val (leafPath, localIndex) = findLeafForPosition(rankedRoot, position)
        val files = getFilesInLeaf(leafPath)
        val (path, origName) = files[localIndex]
        return Photo(originalName = origName, path = path)
    }

    fun positionOf(photoPath: String): Int {
        return findPosition(rankedRoot, photoPath, 0)
    }

    fun movePhoto(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return

        val photo = photoAt(fromPosition)
        val origName = photo.originalName

        // Remove from current position (but keep the file by moving to temp)
        val (leafPath, localIndex) = findLeafForPosition(rankedRoot, fromPosition)
        val files = getFilesInLeaf(leafPath)
        val (filePath, _) = files[localIndex]
        val tempPath = "$rankedRoot/.moving_$origName"
        fs.move(filePath, tempPath)

        // Reprefix remaining in source leaf
        val remaining = files.toMutableList()
        remaining.removeAt(localIndex)
        rewriteLeaf(leafPath, remaining)
        handleUnderflow(leafPath)

        // The target position refers to the final position in the result.
        // After removal, positions >= fromPosition shift down by 1.
        // To end up at `toPosition` in the final array:
        // - If moving forward (toPosition > fromPosition): insert at toPosition - 1
        //   in the shrunk array, which becomes toPosition after reinsertion shifts right
        //   Actually no: inserting at index X in a list makes the item end up at index X.
        //   After removal, the array has n-1 items. We want the item at position toPosition
        //   in the n-item result. Since we insert into an (n-1)-item array:
        //   - If toPosition > fromPosition: the slot we want is at toPosition-1 in the
        //     shrunk array (because removal shifted everything above fromPosition down by 1).
        //     BUT we want the item AT toPosition in the final array, so we need insert index = toPosition - 1?
        //     No: insert at index i in an array means the item ends up at index i.
        //     After removing fromPosition=3 from [0..19], positions 4+ shift to 3+.
        //     Inserting at index 9 in the 19-item array: item at index 9. Final 20-item-like
        //     view: 0,1,2, 4,5,6,7,8,9,10,[3],11,...
        //     That's index 9 not 10. Hmm.
        //
        // Let me reconsider: the semantics of movePhoto(from, to) should be:
        //   "Move the photo currently at `from` so that in the final order it is at position `to`"
        //
        // After removing from `from`, the array shrinks by 1. To place the item at final
        // position `to`, we insert at:
        //   - If to > from: index `to - 1` in the shrunk array? Let me verify:
        //     Remove pos 3 from 20 items → 19 items [0,1,2,4,5,6,7,8,9,10,11,...,19]
        //     Insert at index 9 → [0,1,2,4,5,6,7,8,9,ITEM,10,11,...,19] → 20 items
        //     ITEM is at index 9. But we wanted it at index 10.
        //     So we need to insert at index 10: [0,1,2,4,5,6,7,8,9,10,ITEM,11,...,19]
        //     ITEM is at index 10. Correct!
        //     So when to > from: insertIndex = to (not to-1) because the removal was before 
        //     the target, so the target didn't shift.
        //   - If to < from: index `to` in the shrunk array
        //     Remove pos 15 from 20 → 19 items [0,...,14,16,...,19]
        //     Insert at index 2 → [0,1,ITEM,2,...,14,16,...,19]
        //     ITEM at index 2. Correct!
        //     So when to < from: insertIndex = to.
        //
        // Summary: always insert at `to` when to < from. When to > from, the removal
        // at `from` (which is before `to`) shifts the target down, so we insert at to-1
        // ... but wait that contradicts what I just showed.
        //
        // Let me recount: Original [p0,p1,...,p19]. Remove p3 → [p0,p1,p2,p4,...,p19] (19 items).
        // We want the final to be [p0,p1,p2,p4,...,p9,p10,p3,p11,...,p19] with p3 at index 10.
        // In the 19-item array [p0,p1,p2,p4,...,p19], inserting at position 10:
        //   items[0..9] = [p0,p1,p2,p4,p5,p6,p7,p8,p9,p10], then p3, then [p11,...,p19]
        //   → p3 is at index 10. Correct!
        // But wait — in the 19-item shrunk array, position 9 is p10 (since p3 was removed,
        // items shifted: p4→idx3, p5→idx4, ..., p10→idx9, p11→idx10, ...).
        // So inserting at index 10 in the shrunk array: 
        //   [p0,p1,p2,p4,p5,p6,p7,p8,p9,p10, p3, p11,...] = p3 at index 10. YES!
        //
        // So: when to > from, we insert at `to` (not to-1) in the shrunk array.
        //     when to < from, we insert at `to` in the shrunk array.
        // Conclusion: ALWAYS insert at `to` regardless.
        
        val (targetLeaf, targetLocal) = findLeafForPosition(rankedRoot, toPosition)
        insertIntoLeaf(targetLeaf, targetLocal, origName, tempPath)
    }

    fun applyComparison(
        winnerPosition: Int,
        loserPosition: Int,
        winnerIsUnseen: Boolean = false,
        loserIsUnseen: Boolean = false
    ): Pair<Int, Int> {
        val total = totalPhotos()
        val baseDelta = maxOf(1, total / config.nudgeDivisor / 2)

        val winnerDelta = if (winnerIsUnseen) baseDelta * config.unseenBoostMultiplier else baseDelta
        val loserDelta = if (loserIsUnseen) baseDelta * config.unseenBoostMultiplier else baseDelta

        val maxPos = total - 1
        var newWinnerPos = minOf(winnerPosition + winnerDelta, maxPos)
        var newLoserPos = maxOf(loserPosition - loserDelta, 0)

        // Execute moves. Order matters to avoid index shifts.
        // Strategy: always do the one furthest from its target first.
        if (winnerPosition != newWinnerPos || loserPosition != newLoserPos) {
            if (winnerPosition < loserPosition) {
                // Winner is below loser. Move loser down first, then winner up.
                if (loserPosition != newLoserPos) {
                    movePhoto(loserPosition, newLoserPos)
                }
                // Winner position may have shifted
                val currentWinnerPos = if (newLoserPos <= winnerPosition) winnerPosition else winnerPosition
                val currentNewWinnerTarget = newWinnerPos
                if (currentWinnerPos != currentNewWinnerTarget) {
                    movePhoto(currentWinnerPos, currentNewWinnerTarget)
                }
            } else {
                // Winner is above or at loser. Move winner up first, then loser down.
                if (winnerPosition != newWinnerPos) {
                    movePhoto(winnerPosition, newWinnerPos)
                }
                // Loser position may have shifted
                val currentLoserPos = if (winnerPosition <= loserPosition) loserPosition - 1 else loserPosition
                val adjustedNewLoserPos = if (newWinnerPos <= newLoserPos) newLoserPos else newLoserPos
                if (currentLoserPos != adjustedNewLoserPos) {
                    movePhoto(currentLoserPos, adjustedNewLoserPos)
                } else {
                    newLoserPos = currentLoserPos
                }
            }
        }

        return Pair(newWinnerPos, newLoserPos)
    }

    fun allPhotosInOrder(): List<Photo> {
        val result = mutableListOf<Photo>()
        collectPhotos(rankedRoot, result)
        return result
    }

    fun depth(): Int = computeDepth(rankedRoot)

    fun validate(): TreeValidation {
        val errors = mutableListOf<String>()
        validateNode(rankedRoot, isRoot = true, errors)

        val leafDepths = mutableSetOf<Int>()
        collectLeafDepths(rankedRoot, 0, leafDepths)
        if (leafDepths.size > 1) {
            errors.add("Leaves at different depths: $leafDepths")
        }

        return TreeValidation(errors.isEmpty(), errors)
    }

    // ==================== Core tree operations ====================

    private fun isLeaf(path: String): Boolean {
        val children = fs.listChildren(path)
        return children.none { fs.isDirectory(it) && !fs.fileName(it).startsWith(".") }
    }

    private fun getSubfolders(path: String): List<String> {
        return fs.listChildren(path)
            .filter { fs.isDirectory(it) && !fs.fileName(it).startsWith(".") }
            .sortedBy { fs.fileName(it).toIntOrNull() ?: 0 }
    }

    /** Get ordered list of (filePath, originalName) for photos in a leaf */
    private fun getFilesInLeaf(leafPath: String): List<Pair<String, String>> {
        return fs.listChildren(leafPath)
            .filter { !fs.isDirectory(it) && !fs.fileName(it).startsWith(".") }
            .sortedBy { prefixIndex(fs.fileName(it)).let { p -> if (p < 0) Int.MAX_VALUE else p } }
            .map { Pair(it, stripPrefix(fs.fileName(it))) }
    }

    private fun getPhotosInLeaf(leafPath: String): List<Photo> {
        return getFilesInLeaf(leafPath).map { (path, origName) ->
            Photo(originalName = origName, path = path)
        }
    }

    private fun countPhotos(path: String): Int {
        if (isLeaf(path)) {
            return fs.listChildren(path).count { !fs.isDirectory(it) && !fs.fileName(it).startsWith(".") }
        }
        return getSubfolders(path).sumOf { countPhotos(it) }
    }

    private fun countLeaves(path: String): Int {
        if (isLeaf(path)) return 1
        return getSubfolders(path).sumOf { countLeaves(it) }
    }

    private fun computeDepth(path: String): Int {
        if (isLeaf(path)) return 1
        val first = getSubfolders(path).firstOrNull() ?: return 1
        return 1 + computeDepth(first)
    }

    private fun collectPhotos(path: String, result: MutableList<Photo>) {
        if (isLeaf(path)) {
            result.addAll(getPhotosInLeaf(path))
            return
        }
        for (child in getSubfolders(path)) {
            collectPhotos(child, result)
        }
    }

    // ==================== Position mapping ====================

    /**
     * Find the leaf and local index for a given global position.
     * For positions within range [0, totalPhotos-1] this returns a valid (leaf, index) pair.
     * For position == totalPhotos (used by insertAt to append), returns (lastLeaf, lastLeaf.size).
     */
    private fun findLeafForPosition(nodePath: String, position: Int): Pair<String, Int> {
        if (isLeaf(nodePath)) {
            return Pair(nodePath, position)
        }

        var remaining = position
        val children = getSubfolders(nodePath)
        for (child in children) {
            val childCount = countPhotos(child)
            if (remaining < childCount) {
                return findLeafForPosition(child, remaining)
            }
            remaining -= childCount
        }

        // Position is at or beyond total count (append case) — go to end of last child
        val lastChild = children.last()
        return if (isLeaf(lastChild)) {
            Pair(lastChild, countPhotos(lastChild))
        } else {
            findLeafForPosition(lastChild, countPhotos(lastChild))
        }
    }

    private fun findPosition(nodePath: String, photoPath: String, offset: Int): Int {
        if (isLeaf(nodePath)) {
            val files = getFilesInLeaf(nodePath)
            val idx = files.indexOfFirst { it.first == photoPath }
            return if (idx >= 0) offset + idx else -1
        }

        var currentOffset = offset
        for (child in getSubfolders(nodePath)) {
            val result = findPosition(child, photoPath, currentOffset)
            if (result >= 0) return result
            currentOffset += countPhotos(child)
        }
        return -1
    }

    // ==================== Insertion ====================

    private fun insertIntoLeaf(leafPath: String, localIndex: Int, originalName: String, sourcePath: String) {
        val currentFiles = getFilesInLeaf(leafPath)

        // Move the new file into the leaf with a temp name
        val tempPath = "$leafPath/.ins_$originalName"
        fs.move(sourcePath, tempPath)

        // Build new ordered list
        val newList = currentFiles.toMutableList()
        newList.add(localIndex, Pair(tempPath, originalName))

        // Rewrite the leaf with correct prefixes
        rewriteLeaf(leafPath, newList)

        // Check overflow
        if (newList.size > config.maxKeys) {
            handleOverflow(leafPath)
        }
    }

    /**
     * Rewrite all files in a leaf with sequential prefixes 0_, 1_, 2_, ...
     * The input list contains (currentPath, originalName) pairs in desired order.
     */
    private fun rewriteLeaf(leafPath: String, files: List<Pair<String, String>>) {
        // Phase 1: rename all to temp names
        val temps = files.mapIndexed { i, (currentPath, origName) ->
            val tempPath = "$leafPath/.t${i}_$origName"
            if (fs.exists(currentPath)) {
                fs.rename(currentPath, tempPath)
            }
            Pair(tempPath, origName)
        }

        // Phase 2: rename to final names
        for ((i, pair) in temps.withIndex()) {
            val (tempPath, origName) = pair
            val finalPath = "$leafPath/${withPrefix(i, origName)}"
            if (fs.exists(tempPath)) {
                fs.rename(tempPath, finalPath)
            }
        }
    }

    // ==================== Overflow handling (split/redistribute) ====================

    private fun handleOverflow(leafPath: String) {
        if (getFilesInLeaf(leafPath).size <= config.maxKeys) return

        // Root leaf overflow: split into two children
        if (leafPath == rankedRoot) {
            splitRootLeaf()
            return
        }

        val parentPath = fs.parent(leafPath)
        val siblings = getSubfolders(parentPath)
        val myIndex = siblings.indexOfFirst { it == leafPath }

        // Try redistribute to right sibling
        if (myIndex < siblings.size - 1) {
            val rightSibling = siblings[myIndex + 1]
            if (isLeaf(rightSibling) && getFilesInLeaf(rightSibling).size < config.maxKeys) {
                redistributeLeaves(leafPath, rightSibling)
                return
            }
        }

        // Try redistribute to left sibling
        if (myIndex > 0) {
            val leftSibling = siblings[myIndex - 1]
            if (isLeaf(leftSibling) && getFilesInLeaf(leftSibling).size < config.maxKeys) {
                redistributeLeaves(leftSibling, leafPath)
                return
            }
        }

        // 2→3 split
        if (myIndex < siblings.size - 1) {
            split2To3Leaves(parentPath, myIndex, myIndex + 1)
        } else if (myIndex > 0) {
            split2To3Leaves(parentPath, myIndex - 1, myIndex)
        } else {
            // Only child - shouldn't happen if tree is well-formed, but handle gracefully
            splitRootLeaf()
        }
    }

    private fun splitRootLeaf() {
        val files = getFilesInLeaf(rankedRoot)
        val mid = files.size / 2

        val leftFiles = files.subList(0, mid)
        val rightFiles = files.subList(mid, files.size)

        // Create child directories
        fs.createDirectory("$rankedRoot/0")
        fs.createDirectory("$rankedRoot/1")

        // Move files to children with proper prefixes
        writeFilesToLeaf("$rankedRoot/0", leftFiles)
        writeFilesToLeaf("$rankedRoot/1", rightFiles)
    }

    /**
     * Evenly redistribute files between two adjacent leaves.
     */
    private fun redistributeLeaves(leftLeaf: String, rightLeaf: String) {
        val leftFiles = getFilesInLeaf(leftLeaf)
        val rightFiles = getFilesInLeaf(rightLeaf)
        val all = leftFiles + rightFiles
        val mid = all.size / 2

        // Move all files to temp names (they stay in their respective directories)
        val allTemps = moveAllToTemp(leftLeaf, leftFiles) + moveAllToTemp(rightLeaf, rightFiles)

        // Write to final positions (moves from temp paths to proper prefixed names)
        writeFilesToLeaf(leftLeaf, allTemps.subList(0, mid))
        writeFilesToLeaf(rightLeaf, allTemps.subList(mid, allTemps.size))
    }

    /**
     * 2→3 split for leaves: two full leaves become three.
     */
    private fun split2To3Leaves(parentPath: String, leftIdx: Int, rightIdx: Int) {
        val siblings = getSubfolders(parentPath)
        val leftLeaf = siblings[leftIdx]
        val rightLeaf = siblings[rightIdx]

        val leftFiles = getFilesInLeaf(leftLeaf)
        val rightFiles = getFilesInLeaf(rightLeaf)
        val all = leftFiles + rightFiles

        // Divide into 3 groups
        val third = all.size / 3

        // Move all files to temp locations
        val allTemps = moveAllToTemp(leftLeaf, leftFiles) + moveAllToTemp(rightLeaf, rightFiles)
        val g1Temps = allTemps.subList(0, third)
        val g2Temps = allTemps.subList(third, third * 2)
        val g3Temps = allTemps.subList(third * 2, allTemps.size)

        // Create middle leaf
        val middlePath = "$parentPath/.mid"
        fs.createDirectory(middlePath)

        // Write groups
        writeFilesToLeaf(leftLeaf, g1Temps)
        writeFilesToLeaf(middlePath, g2Temps)
        writeFilesToLeaf(rightLeaf, g3Temps)

        // Insert middle leaf between leftIdx and rightIdx by renumbering
        insertChildAt(parentPath, leftIdx + 1, middlePath)

        // Check parent overflow
        if (getSubfolders(parentPath).size > config.maxChildren) {
            handleInternalOverflow(parentPath)
        }
    }

    private fun splitRootInternal() {
        val children = getSubfolders(rankedRoot)
        val mid = children.size / 2

        val leftChildren = children.subList(0, mid)
        val rightChildren = children.subList(mid, children.size)

        // Move to temp first
        val leftTemps = leftChildren.mapIndexed { i, c ->
            val t = "$rankedRoot/.sl$i"
            fs.move(c, t)
            t
        }
        val rightTemps = rightChildren.mapIndexed { i, c ->
            val t = "$rankedRoot/.sr$i"
            fs.move(c, t)
            t
        }

        // Create two new internal nodes
        fs.createDirectory("$rankedRoot/0")
        fs.createDirectory("$rankedRoot/1")

        for ((i, t) in leftTemps.withIndex()) {
            fs.move(t, "$rankedRoot/0/$i")
        }
        for ((i, t) in rightTemps.withIndex()) {
            fs.move(t, "$rankedRoot/1/$i")
        }
    }

    private fun handleInternalOverflow(nodePath: String) {
        val children = getSubfolders(nodePath)
        if (children.size <= config.maxChildren) return

        if (nodePath == rankedRoot) {
            splitRootInternal()
            return
        }

        val parentPath = fs.parent(nodePath)
        val parentChildren = getSubfolders(parentPath)
        val myIndex = parentChildren.indexOfFirst { it == nodePath }

        // Try redistribute to right sibling
        if (myIndex < parentChildren.size - 1) {
            val right = parentChildren[myIndex + 1]
            if (getSubfolders(right).size < config.maxChildren) {
                redistributeInternal(nodePath, right)
                return
            }
        }

        // Try redistribute to left sibling
        if (myIndex > 0) {
            val left = parentChildren[myIndex - 1]
            if (getSubfolders(left).size < config.maxChildren) {
                redistributeInternal(left, nodePath)
                return
            }
        }

        // 2→3 split for internal
        if (myIndex < parentChildren.size - 1) {
            split2To3Internal(parentPath, myIndex, myIndex + 1)
        } else if (myIndex > 0) {
            split2To3Internal(parentPath, myIndex - 1, myIndex)
        } else {
            splitRootInternal()
        }
    }

    private fun redistributeInternal(leftNode: String, rightNode: String) {
        val leftChildren = getSubfolders(leftNode)
        val rightChildren = getSubfolders(rightNode)

        // Move all children to temp
        val allTemps = leftChildren.mapIndexed { i, c ->
            val t = "$leftNode/.ri$i"
            fs.move(c, t)
            t
        } + rightChildren.mapIndexed { i, c ->
            val t = "$rightNode/.ri${leftChildren.size + i}"
            fs.move(c, t)
            t
        }

        val mid = allTemps.size / 2

        // Distribute
        for ((i, t) in allTemps.subList(0, mid).withIndex()) {
            fs.move(t, "$leftNode/$i")
        }
        for ((i, t) in allTemps.subList(mid, allTemps.size).withIndex()) {
            fs.move(t, "$rightNode/$i")
        }
    }

    private fun split2To3Internal(parentPath: String, leftIdx: Int, rightIdx: Int) {
        val siblings = getSubfolders(parentPath)
        val leftNode = siblings[leftIdx]
        val rightNode = siblings[rightIdx]

        val leftChildren = getSubfolders(leftNode)
        val rightChildren = getSubfolders(rightNode)

        // Move all to temp
        val allTemps = leftChildren.mapIndexed { i, c ->
            val t = "$leftNode/.si$i"
            fs.move(c, t)
            t
        } + rightChildren.mapIndexed { i, c ->
            val t = "$rightNode/.si${leftChildren.size + i}"
            fs.move(c, t)
            t
        }

        val third = allTemps.size / 3
        val g1 = allTemps.subList(0, third)
        val g2 = allTemps.subList(third, third * 2)
        val g3 = allTemps.subList(third * 2, allTemps.size)

        // Create middle node
        val middlePath = "$parentPath/.mid"
        fs.createDirectory(middlePath)

        for ((i, t) in g1.withIndex()) { fs.move(t, "$leftNode/$i") }
        for ((i, t) in g2.withIndex()) { fs.move(t, "$middlePath/$i") }
        for ((i, t) in g3.withIndex()) { fs.move(t, "$rightNode/$i") }

        insertChildAt(parentPath, leftIdx + 1, middlePath)

        if (getSubfolders(parentPath).size > config.maxChildren) {
            handleInternalOverflow(parentPath)
        }
    }

    // ==================== Underflow handling (merge/redistribute) ====================

    private fun handleUnderflow(leafPath: String) {
        // Root can have any count
        if (leafPath == rankedRoot) return

        val parentPath = fs.parent(leafPath)
        val siblings = getSubfolders(parentPath)

        // If parent is root with only one child, collapse
        if (parentPath == rankedRoot && siblings.size == 1) {
            collapseRootSingleChild()
            return
        }

        val fileCount = getFilesInLeaf(leafPath).size
        if (fileCount >= config.minKeys) return

        val myIndex = siblings.indexOfFirst { it == leafPath }

        // Try redistribute from right sibling
        if (myIndex < siblings.size - 1) {
            val right = siblings[myIndex + 1]
            if (isLeaf(right) && getFilesInLeaf(right).size > config.minKeys) {
                redistributeLeaves(leafPath, right)
                return
            }
        }

        // Try redistribute from left sibling
        if (myIndex > 0) {
            val left = siblings[myIndex - 1]
            if (isLeaf(left) && getFilesInLeaf(left).size > config.minKeys) {
                redistributeLeaves(left, leafPath)
                return
            }
        }

        // Merge with a sibling
        if (myIndex < siblings.size - 1) {
            mergeLeaves(parentPath, myIndex, myIndex + 1)
        } else if (myIndex > 0) {
            mergeLeaves(parentPath, myIndex - 1, myIndex)
        }
    }

    private fun mergeLeaves(parentPath: String, leftIdx: Int, rightIdx: Int) {
        val siblings = getSubfolders(parentPath)
        val leftLeaf = siblings[leftIdx]
        val rightLeaf = siblings[rightIdx]

        val leftFiles = getFilesInLeaf(leftLeaf)
        val rightFiles = getFilesInLeaf(rightLeaf)
        val all = leftFiles + rightFiles

        if (all.size <= config.maxKeys) {
            // Merge into left
            val allTemps = moveAllToTemp(leftLeaf, leftFiles) + moveAllToTemp(rightLeaf, rightFiles)
            writeFilesToLeaf(leftLeaf, allTemps)
            fs.delete(rightLeaf)
            removeChildAt(parentPath, rightIdx)
            handleInternalUnderflow(parentPath)
        } else {
            // Can't fully merge — redistribute evenly
            redistributeLeaves(leftLeaf, rightLeaf)
        }
    }

    private fun handleInternalUnderflow(nodePath: String) {
        if (nodePath == rankedRoot) {
            val children = getSubfolders(rankedRoot)
            if (children.size == 1) {
                collapseRootSingleChild()
            }
            return
        }

        val children = getSubfolders(nodePath)
        if (children.size >= config.minChildren) return

        val parentPath = fs.parent(nodePath)
        val parentChildren = getSubfolders(parentPath)
        val myIndex = parentChildren.indexOfFirst { it == nodePath }

        // Try redistribute from right
        if (myIndex < parentChildren.size - 1) {
            val right = parentChildren[myIndex + 1]
            if (getSubfolders(right).size > config.minChildren) {
                redistributeInternal(nodePath, right)
                return
            }
        }

        // Try redistribute from left
        if (myIndex > 0) {
            val left = parentChildren[myIndex - 1]
            if (getSubfolders(left).size > config.minChildren) {
                redistributeInternal(left, nodePath)
                return
            }
        }

        // Merge internal nodes
        if (myIndex < parentChildren.size - 1) {
            mergeInternal(parentPath, myIndex, myIndex + 1)
        } else if (myIndex > 0) {
            mergeInternal(parentPath, myIndex - 1, myIndex)
        }
    }

    private fun mergeInternal(parentPath: String, leftIdx: Int, rightIdx: Int) {
        val siblings = getSubfolders(parentPath)
        val leftNode = siblings[leftIdx]
        val rightNode = siblings[rightIdx]

        val leftChildren = getSubfolders(leftNode)
        val rightChildren = getSubfolders(rightNode)
        val total = leftChildren.size + rightChildren.size

        if (total <= config.maxChildren) {
            // Merge right into left
            val rightTemps = rightChildren.mapIndexed { i, c ->
                val t = "$rightNode/.mg$i"
                fs.move(c, t)
                t
            }
            val baseIdx = leftChildren.size
            for ((i, t) in rightTemps.withIndex()) {
                fs.move(t, "$leftNode/${baseIdx + i}")
            }
            fs.delete(rightNode)
            removeChildAt(parentPath, rightIdx)
            handleInternalUnderflow(parentPath)
        } else {
            redistributeInternal(leftNode, rightNode)
        }
    }

    private fun collapseRootSingleChild() {
        val children = getSubfolders(rankedRoot)
        if (children.size != 1) return

        val onlyChild = children[0]
        if (isLeaf(onlyChild)) {
            // Move photos up to root
            val files = getFilesInLeaf(onlyChild)
            for ((path, _) in files) {
                val name = fs.fileName(path)
                fs.move(path, "$rankedRoot/$name")
            }
        } else {
            // Move grandchildren up
            val grandchildren = getSubfolders(onlyChild)
            val temps = grandchildren.mapIndexed { i, gc ->
                val t = "$rankedRoot/.col$i"
                fs.move(gc, t)
                t
            }
            for ((i, t) in temps.withIndex()) {
                fs.move(t, "$rankedRoot/$i")
            }
        }
        fs.delete(onlyChild)
    }

    // ==================== Helpers ====================

    /**
     * Move all files in a leaf to temp names, returning (tempPath, originalName) list.
     */
    private fun moveAllToTemp(leafPath: String, files: List<Pair<String, String>>): List<Pair<String, String>> {
        return files.mapIndexed { i, (path, origName) ->
            val tempPath = "$leafPath/.mv${i}_$origName"
            fs.rename(path, tempPath)
            Pair(tempPath, origName)
        }
    }

    /**
     * Write files to a leaf with proper prefixes. Files are moved from their current paths.
     */
    private fun writeFilesToLeaf(leafPath: String, files: List<Pair<String, String>>) {
        for ((i, pair) in files.withIndex()) {
            val (sourcePath, origName) = pair
            val targetPath = "$leafPath/${withPrefix(i, origName)}"
            fs.move(sourcePath, targetPath)
        }
    }

    /**
     * Delete all non-directory, non-hidden files in a directory.
     */
    private fun clearAllFiles(dirPath: String) {
        fs.listChildren(dirPath)
            .filter { !fs.isDirectory(it) }
            .forEach { fs.delete(it) }
    }

    /**
     * Insert a new child directory at the given index within a parent.
     * Renumbers existing children to make room.
     */
    private fun insertChildAt(parentPath: String, insertIndex: Int, newChildPath: String) {
        val siblings = getSubfolders(parentPath)

        // Rename siblings >= insertIndex to make room (from high to low to avoid collisions)
        for (i in siblings.size - 1 downTo insertIndex) {
            val oldPath = siblings[i]
            val newIdx = i + 1
            val tempPath = "$parentPath/.ren$newIdx"
            fs.move(oldPath, tempPath)
        }

        // Place the new child
        val targetPath = "$parentPath/$insertIndex"
        fs.move(newChildPath, targetPath)

        // Rename temps to final
        for (i in insertIndex + 1..siblings.size) {
            val tempPath = "$parentPath/.ren$i"
            if (fs.exists(tempPath)) {
                fs.move(tempPath, "$parentPath/$i")
            }
        }
    }

    /**
     * Remove a child at the given index, renumbering remaining children.
     */
    private fun removeChildAt(parentPath: String, removeIndex: Int) {
        val siblings = getSubfolders(parentPath)

        // Rename children after removeIndex (shift down)
        for (i in removeIndex + 1 until siblings.size) {
            val oldPath = siblings[i]
            val tempPath = "$parentPath/.rd${i - 1}"
            fs.move(oldPath, tempPath)
        }
        for (i in removeIndex until siblings.size - 1) {
            val tempPath = "$parentPath/.rd$i"
            if (fs.exists(tempPath)) {
                fs.move(tempPath, "$parentPath/$i")
            }
        }
    }

    // ==================== Validation ====================

    private fun validateNode(path: String, isRoot: Boolean, errors: MutableList<String>, parentIsRoot: Boolean = false) {
        if (isLeaf(path)) {
            val count = getFilesInLeaf(path).size
            // Root is always exempt from minimum. Root's direct children are also exempt
            // because after the first root split, children may have fewer than minKeys.
            if (!isRoot && !parentIsRoot && count < config.minKeys) {
                errors.add("Leaf '$path' has $count photos (min: ${config.minKeys})")
            }
            if (count > config.maxKeys) {
                errors.add("Leaf '$path' has $count photos (max: ${config.maxKeys})")
            }
            // Check prefix ordering
            val files = getFilesInLeaf(path)
            for ((i, pair) in files.withIndex()) {
                val actualPrefix = prefixIndex(fs.fileName(pair.first))
                if (actualPrefix != i) {
                    errors.add("Leaf '$path': file at index $i has prefix $actualPrefix (${fs.fileName(pair.first)})")
                }
            }
        } else {
            val children = getSubfolders(path)
            if (!isRoot && !parentIsRoot && children.size < config.minChildren) {
                errors.add("Internal '$path' has ${children.size} children (min: ${config.minChildren})")
            }
            if (children.size > config.maxChildren) {
                errors.add("Internal '$path' has ${children.size} children (max: ${config.maxChildren})")
            }
            for (child in children) {
                validateNode(child, isRoot = false, errors, parentIsRoot = isRoot)
            }
        }
    }

    private fun collectLeafDepths(path: String, depth: Int, depths: MutableSet<Int>) {
        if (isLeaf(path)) {
            depths.add(depth)
            return
        }
        for (child in getSubfolders(path)) {
            collectLeafDepths(child, depth + 1, depths)
        }
    }
}

data class TreeValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList()
)
