@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import platform.fsstat.cdm_fs_free
import platform.fsstat.cdm_fs_total

/**
 * Filesystem space via statvfs C helpers (Apple + Linux + Android).
 * mingw has its own Win32 implementation.
 */
actual fun platformFreeSpace(path: String): Long {
    val v = cdm_fs_free(path)
    return if (v < 0) 0 else v
}

actual fun platformTotalSpace(path: String): Long {
    val v = cdm_fs_total(path)
    return if (v < 0) 0 else v
}
