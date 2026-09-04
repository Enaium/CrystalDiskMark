@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import kotlinx.cinterop.*
import platform.windisk.*

/** Windows (mingw) file operations via the windisk cinterop. */
internal actual object NativeFileOps {
    actual fun openFile(path: String, create: Boolean): Long {
        val h = cdm_win_open(path, if (create) 1 else 0)
        if (h == 0L) throw RuntimeException("CreateFile failed: $path")
        return h
    }

    actual fun setLength(fd: Long, length: Long) {
        if (cdm_win_set_length(fd, length) != 0) {
            throw RuntimeException("SetEndOfFile failed: fd=$fd")
        }
    }

    actual fun readAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int): Int {
        if (len == 0) return 0
        val n = buffer.usePinned { pinned ->
            cdm_win_read(fd, pos, pinned.addressOf(offset), len.toLong())
        }
        return n.toInt()
    }

    actual fun writeAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int) {
        if (len == 0) return
        val rc = buffer.usePinned { pinned ->
            cdm_win_write(fd, pos, pinned.addressOf(offset), len.toLong())
        }
        if (rc != 0) throw RuntimeException("WriteFile failed: fd=$fd")
    }

    actual fun close(fd: Long) = cdm_win_close(fd)

    actual fun delete(path: String) {
        // Files via DeleteFileA; directories via RemoveDirectoryA. One of
        // the two is a no-op failure for the other kind.
        cdm_win_delete(path)
        cdm_win_rmdir(path)
    }

    actual fun exists(path: String): Boolean = cdm_win_exists(path) != 0

    actual fun mkdir(path: String) = cdm_win_mkdir(path)

    actual fun listDirectories(path: String): List<String> {
        val cstr = cdm_win_list_dirs(path) ?: return emptyList()
        try {
            val text = cstr.toKString()
            return text.lineSequence().filter { it.isNotEmpty() }.toList()
        } finally {
            cdm_win_free_str(cstr)
        }
    }

    actual fun fileSize(path: String): Long {
        val v = cdm_win_file_size(path)
        return if (v < 0) 0 else v
    }

    actual fun readTextFile(path: String): String? {
        if (!exists(path)) return null
        val size = fileSize(path)
        if (size <= 0) return ""
        val fd = openFile(path, false)
        try {
            val bytes = ByteArray(size.toInt())
            var off = 0
            while (off < bytes.size) {
                val n = readAt(fd, off.toLong(), bytes, off, bytes.size - off)
                if (n <= 0) break
                off += n
            }
            return bytes.decodeToString(0, off)
        } finally {
            close(fd)
        }
    }

    actual fun writeTextFile(path: String, content: String) {
        val fd = openFile(path, true)
        try {
            val bytes = content.encodeToByteArray()
            var off = 0
            while (off < bytes.size) {
                val len = minOf(bytes.size - off, 1024 * 1024)
                writeAt(fd, off.toLong(), bytes, off, len)
                off += len
            }
            setLength(fd, bytes.size.toLong())
        } finally {
            close(fd)
        }
    }
}
