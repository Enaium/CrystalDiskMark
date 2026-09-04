@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import platform.windisk.cdm_win_free
import platform.windisk.cdm_win_total

/** Windows (mingw): GetDiskFreeSpaceExA via the windisk cinterop. */
actual fun platformFreeSpace(path: String): Long {
    val v = cdm_win_free(path)
    return if (v < 0) 0 else v
}

actual fun platformTotalSpace(path: String): Long {
    val v = cdm_win_total(path)
    return if (v < 0) 0 else v
}
