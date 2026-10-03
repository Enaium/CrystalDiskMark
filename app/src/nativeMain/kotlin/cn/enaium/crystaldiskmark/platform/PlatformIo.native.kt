@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package cn.enaium.crystaldiskmark.platform

import kotlinx.cinterop.*
import platform.posix.*

private class PosixRandomAccessFile(private val fd: Long) : RandomAccessFile {
    override fun setLength(length: Long) = NativeFileOps.setLength(fd, length)

    override fun readAt(pos: Long, buffer: ByteArray, offset: Int, len: Int): Int =
        NativeFileOps.readAt(fd, pos, buffer, offset, len)

    override fun writeAt(pos: Long, buffer: ByteArray, offset: Int, len: Int) =
        NativeFileOps.writeAt(fd, pos, buffer, offset, len)

    override fun close() = NativeFileOps.close(fd)
}

/**
 * Shared Kotlin/Native PlatformIo: macOS / iOS / tvOS / Linux / Android / mingw.
 * File operations delegate to NativeFileOps (POSIX or Win32 actual).
 */
object NativePlatformIo : PlatformIo {

    override fun openFile(path: String, create: Boolean): RandomAccessFile =
        PosixRandomAccessFile(NativeFileOps.openFile(path, create))

    override fun deleteFile(path: String) = NativeFileOps.delete(path)

    override fun fileExists(path: String): Boolean = NativeFileOps.exists(path)

    override fun createDirectory(path: String) = NativeFileOps.mkdir(path)

    override fun listDirectories(path: String): List<String> = NativeFileOps.listDirectories(path)

    override fun spawnThread(name: String, block: () -> Unit): ThreadHandle {
        return spawnNativeThread(name, block)
    }

    override fun freeSpace(path: String): Long = platformFreeSpace(path)

    override fun totalSpace(path: String): Long = platformTotalSpace(path)

    override fun homeDir(): String {
        val env = getenv("HOME") ?: return "/"
        return env.toKString()
    }

    override fun windowSafeInsets(): IntArray? {
        // Published by the Android MainActivity via nativeSetenv; absent
        // on desktop platforms (returns null -> full-window layout).
        val l = getenv("CDM_SAFE_LEFT")?.toKString()?.toIntOrNull() ?: return null
        val r = getenv("CDM_SAFE_RIGHT")?.toKString()?.toIntOrNull() ?: return null
        val t = getenv("CDM_SAFE_TOP")?.toKString()?.toIntOrNull() ?: return null
        val b = getenv("CDM_SAFE_BOTTOM")?.toKString()?.toIntOrNull() ?: return null
        return intArrayOf(l, r, t, b)
    }

    override fun configDir(): String {
        val home = homeDir()
        val dir = "$home/.CrystalDiskMark"
        createDirectory(dir)
        return dir
    }

    override fun osName(): String = platformOsName()

    override fun cpuName(): String = platformCpuName()

    override fun cpuCores(): Pair<Int, Int> = platformCpuCores()

    override fun totalMemory(): Long = platformTotalMemory()

    override fun readTextFile(path: String): String? = NativeFileOps.readTextFile(path)

    override fun readFileHeader(path: String, maxBytes: Int): ByteArray? = NativeFileOps.readHeader(path, maxBytes)

    override fun writeTextFile(path: String, content: String) = NativeFileOps.writeTextFile(path, content)

    override fun fileSize(path: String): Long = NativeFileOps.fileSize(path)

    private val monotonicEpoch = kotlin.time.TimeSource.Monotonic.markNow()

    override fun currentTimeMillis(): Long {
        // Monotonic clock; relative timing only (durations, deadlines).
        return monotonicEpoch.elapsedNow().inWholeMilliseconds
    }

    override fun nanoTime(): Long {
        return monotonicEpoch.elapsedNow().inWholeNanoseconds
    }

    override fun sleep(ms: Long) {
        if (ms <= 0) return
        // usleep is in unistd.h on Apple/Linux/mingw.
        usleep((ms * 1000).toUInt())
    }

    override fun log(message: String) = println(message)
}

/** POSIX thread creation via pthread (Apple/Linux/mingw/Android native). */
@OptIn(ExperimentalForeignApi::class)
private fun spawnNativeThread(name: String, block: () -> Unit): ThreadHandle {
    val done = kotlin.concurrent.atomics.AtomicInt(0)
    val stableRef = StableRef.create {
        try {
            block()
        } finally {
            done.store(1)
        }
    }
    val thread = nativeHeap.alloc<pthread_tVar>()
    val rc = pthread_create(
        thread.ptr,
        null,
        staticCFunction { arg ->
            // Detach so no pthread_join is needed; the thread frees itself.
            pthread_detach(pthread_self())
            val ref = arg?.asStableRef<() -> Unit>()
            ref?.get()?.invoke()
            ref?.dispose()
            null
        },
        stableRef.asCPointer(),
    )
    if (rc != 0) {
        stableRef.dispose()
        nativeHeap.free(thread)
        throw RuntimeException("pthread_create failed: $rc")
    }
    nativeHeap.free(thread)
    return object : ThreadHandle {
        override fun join() {
            while (done.load() == 0) {
                usleep(1000u)
            }
        }
    }
}

actual fun platformIo(): PlatformIo = NativePlatformIo
