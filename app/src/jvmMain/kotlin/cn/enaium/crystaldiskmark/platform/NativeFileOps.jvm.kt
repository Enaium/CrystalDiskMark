package cn.enaium.crystaldiskmark.platform

import java.io.File
import java.io.RandomAccessFile

/** Module-level open-file registry used by the JVM NativeFileOps actual. */
private val jvmFileHandles = java.util.concurrent.ConcurrentHashMap<Long, RandomAccessFile>()
private val jvmFileCounter = java.util.concurrent.atomic.AtomicLong(1)

/** JVM file operations backed by java.io. */
internal actual object NativeFileOps {
    actual fun openFile(path: String, create: Boolean): Long {
        val f = File(path)
        if (!f.exists() && !create) throw RuntimeException("open failed: $path")
        val raf = RandomAccessFile(f, "rw")
        if (create) raf.setLength(0)
        val id = jvmFileCounter.getAndIncrement()
        jvmFileHandles[id] = raf
        return id
    }

    actual fun setLength(fd: Long, length: Long) = handle(fd).setLength(length)

    actual fun readAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int): Int {
        val raf = handle(fd)
        synchronized(raf) {
            raf.seek(pos)
            return raf.read(buffer, offset, len)
        }
    }

    actual fun writeAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int) {
        val raf = handle(fd)
        synchronized(raf) {
            raf.seek(pos)
            raf.write(buffer, offset, len)
        }
    }

    actual fun close(fd: Long) {
        jvmFileHandles.remove(fd)?.close()
    }

    actual fun delete(path: String) {
        val f = File(path)
        if (f.isDirectory) f.deleteRecursively() else f.delete()
    }

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun mkdir(path: String) {
        File(path).mkdirs()
    }

    actual fun listDirectories(path: String): List<String> =
        File(path).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    actual fun fileSize(path: String): Long = File(path).length()

    actual fun readTextFile(path: String): String? {
        val f = File(path)
        if (!f.exists()) return null
        return f.readText(Charsets.UTF_8)
    }

    actual fun writeTextFile(path: String, content: String) {
        File(path).writeText(content, Charsets.UTF_8)
    }

    private fun handle(fd: Long): RandomAccessFile =
        jvmFileHandles[fd] ?: error("bad fd $fd")
}
