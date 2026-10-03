@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import kotlinx.cinterop.*
import platform.posix.*

/** POSIX (Apple/Linux/Android) file operations. */
internal actual object NativeFileOps {
    actual fun openFile(path: String, create: Boolean): Long {
        val flags = if (create) O_RDWR or O_CREAT else O_RDWR
        val fd = open(path, flags, 0x1B6u /* 0666 */)
        if (fd < 0) throw RuntimeException("open failed: $path errno=$errno")
        return fd.toLong()
    }

    actual fun setLength(fd: Long, length: Long) {
        if (ftruncate(fd.toInt(), length) != 0) {
            throw RuntimeException("ftruncate failed: fd=$fd errno=$errno")
        }
    }

    actual fun readAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int): Int {
        if (len == 0) return 0
        val n = buffer.usePinned { pinned ->
            pread(fd.toInt(), pinned.addressOf(offset), len.toULong(), pos.toLong())
        }
        return n.toInt()
    }

    actual fun writeAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int) {
        if (len == 0) return
        val n = buffer.usePinned { pinned ->
            pwrite(fd.toInt(), pinned.addressOf(offset), len.toULong(), pos.toLong())
        }
        if (n.toInt() != len) {
            throw RuntimeException("pwrite failed: wrote $n of $len errno=$errno")
        }
    }

    actual fun close(fd: Long) {
        close(fd.toInt())
    }

    actual fun delete(path: String) {
        // Files are removed with unlink; directories (empty) with rmdir.
        if (unlink(path) != 0) {
            rmdir(path)
        }
    }

    actual fun exists(path: String): Boolean = access(path, F_OK) == 0

    actual fun mkdir(path: String) {
        mkdir(path, 0x1EDu /* 0755 */)
    }

    actual fun listDirectories(path: String): List<String> {
        val result = mutableListOf<String>()
        val dir = opendir(path) ?: return emptyList()
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                if (entry.pointed.d_type.toInt() == 4 /* DT_DIR */) {
                    result.add(name)
                }
            }
        } finally {
            closedir(dir)
        }
        return result
    }

    actual fun fileSize(path: String): Long {
        val fd = open(path, O_RDONLY)
        if (fd < 0) return 0
        try {
            return lseek(fd, 0L, SEEK_END)
        } finally {
            close(fd)
        }
    }

    actual fun readTextFile(path: String): String? {
        if (!exists(path)) return null
        val fd = open(path, O_RDONLY)
        if (fd < 0) return null
        try {
            val size = lseek(fd, 0L, SEEK_END)
            lseek(fd, 0L, SEEK_SET)
            if (size <= 0) return ""
            val bytes = ByteArray(size.toInt())
            var off = 0
            while (off < bytes.size) {
                val n = bytes.usePinned { read(fd, it.addressOf(off), (bytes.size - off).toULong()) }
                if (n <= 0L) break
                off += n.toInt()
            }
            return bytes.decodeToString(0, off)
        } finally {
            close(fd)
        }
    }

    actual fun writeTextFile(path: String, content: String) {
        val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x1B6u)
        if (fd < 0) throw RuntimeException("open for write failed: $path errno=$errno")
        try {
            val bytes = content.encodeToByteArray()
            var off = 0
            while (off < bytes.size) {
                val n = bytes.usePinned { write(fd, it.addressOf(off), (bytes.size - off).toULong()) }
                if (n <= 0L) throw RuntimeException("write failed: $path errno=$errno")
                off += n.toInt()
            }
        } finally {
            close(fd)
        }
    }

    actual fun readHeader(path: String, maxBytes: Int): ByteArray? {
        val fd = open(path, O_RDONLY)
        if (fd < 0) return null
        try {
            val buf = ByteArray(maxBytes)
            var off = 0
            while (off < buf.size) {
                val n = buf.usePinned { read(fd, it.addressOf(off), (buf.size - off).toULong()) }
                if (n <= 0L) break
                off += n.toInt()
            }
            return if (off == 0) null else buf.copyOf(off)
        } finally {
            close(fd)
        }
    }
}
