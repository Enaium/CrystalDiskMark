@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.windisk.cdm_win_cores
import platform.windisk.cdm_win_memory

/** Windows (mingw): environment + Win32 helpers. */
actual fun platformCpuName(): String {
    val env = getenv("PROCESSOR_IDENTIFIER")
    return env?.toKString() ?: "Windows CPU"
}

actual fun platformCpuCores(): Pair<Int, Int> {
    val logical = cdm_win_cores().toInt().takeIf { it > 0 } ?: 1
    return Pair(logical, logical)
}

actual fun platformTotalMemory(): Long {
    val v = cdm_win_memory()
    return if (v < 0) 0 else v
}

/** Windows OS name. */
actual fun platformOsName(): String {
    val env = getenv("OS")
    return env?.toKString() ?: "Windows"
}
