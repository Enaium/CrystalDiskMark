package cn.enaium.crystaldiskmark.platform

/**
 * Random-access file handle for benchmark I/O.
 * Implementations are thread-safe for concurrent readAt/writeAt.
 */
interface RandomAccessFile {
    /** Sets the file length (truncate/extend). */
    fun setLength(length: Long)

    /** Reads up to [len] bytes at [pos]; returns bytes read, or -1 at EOF. */
    fun readAt(pos: Long, buffer: ByteArray, offset: Int, len: Int): Int

    /** Writes [len] bytes at [pos]. */
    fun writeAt(pos: Long, buffer: ByteArray, offset: Int, len: Int)

    fun close()
}

/** A platform thread handle. */
interface ThreadHandle {
    fun join()
}

interface PlatformIo {
    /** Opens (creating if needed) a file for read/write. */
    fun openFile(path: String, create: Boolean): RandomAccessFile

    fun deleteFile(path: String)
    fun fileExists(path: String): Boolean
    fun createDirectory(path: String)
    fun listDirectories(path: String): List<String>
    fun fileSize(path: String): Long

    /** Spawns a detached thread running [block]; returns a handle. */
    fun spawnThread(name: String, block: () -> Unit): ThreadHandle

    /** Free bytes on the volume containing [path]. */
    fun freeSpace(path: String): Long

    /** Total bytes on the volume containing [path]. */
    fun totalSpace(path: String): Long

    /** Home directory. */
    fun homeDir(): String

    /**
     * Irregular-screen safe-area insets in **screen pixels**
     * (left, right, top, bottom), or null when the platform does not
     * report any (desktop). Used on Android to keep the UI clear of
     * punch holes and rounded display corners.
     */
    fun windowSafeInsets(): IntArray?

    /** Config file directory (created if needed). */
    fun configDir(): String

    /** User-visible OS name, e.g. "Windows 11 Pro". */
    fun osName(): String

    /** CPU model name. */
    fun cpuName(): String

    /** Physical/logical core counts. */
    fun cpuCores(): Pair<Int, Int>

    /** Total physical memory in bytes. */
    fun totalMemory(): Long

    /** Read text file (UTF-8) or null. */
    fun readTextFile(path: String): String?

    /** Reads up to [maxBytes] from the start of [path], or null when the
     *  file cannot be read. Used to sniff binary file headers (fonts). */
    fun readFileHeader(path: String, maxBytes: Int): ByteArray?

    /** Write text file (UTF-8). */
    fun writeTextFile(path: String, content: String)

    /** Current time in milliseconds (monotonic-ish wall clock). */
    fun currentTimeMillis(): Long

    /** High-resolution monotonic clock in nanoseconds (for latency). */
    fun nanoTime(): Long

    /** Sleep for [ms] milliseconds. */
    fun sleep(ms: Long)

    /** Log line to stdout/stderr. */
    fun log(message: String)
}

expect fun platformIo(): PlatformIo

// ---------------------------------------------------------------------------
// Platform-specific queries (expect/actual across source sets).
// ---------------------------------------------------------------------------

/** Platform-specific OS name. */
internal expect fun platformOsName(): String

/** Platform-specific filesystem space queries. */
internal expect fun platformFreeSpace(path: String): Long

internal expect fun platformTotalSpace(path: String): Long

/** Platform-specific CPU name (sysctl on Apple, /proc/cpuinfo on Linux). */
internal expect fun platformCpuName(): String

internal expect fun platformCpuCores(): Pair<Int, Int>

internal expect fun platformTotalMemory(): Long
