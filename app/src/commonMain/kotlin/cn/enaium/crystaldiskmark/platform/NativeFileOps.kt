package cn.enaium.crystaldiskmark.platform

/**
 * Platform file operations that differ between POSIX (pread/pwrite,
 * opendir) and Windows (ReadFile/WriteFile, FindFirstFile).
 * POSIX actual lives in nativeMain; the mingw actual in mingwX64Main.
 * File descriptors are Long to hold 64-bit HANDLEs on Windows.
 */
internal expect object NativeFileOps {
    fun openFile(path: String, create: Boolean): Long
    fun setLength(fd: Long, length: Long)
    fun readAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int): Int
    fun writeAt(fd: Long, pos: Long, buffer: ByteArray, offset: Int, len: Int)
    fun close(fd: Long)
    fun delete(path: String)
    fun exists(path: String): Boolean
    fun mkdir(path: String)
    fun listDirectories(path: String): List<String>
    fun fileSize(path: String): Long
    fun readTextFile(path: String): String?
    fun writeTextFile(path: String, content: String)
}
