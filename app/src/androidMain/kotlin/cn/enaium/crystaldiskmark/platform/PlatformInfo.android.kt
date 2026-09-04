@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import platform.posix.*

/** Android native: read /proc like Linux (via NativeFileOps for ABI-safe IO). */
actual fun platformCpuName(): String {
    val info = readProc("/proc/cpuinfo") ?: return "Android CPU"
    for (line in info.lineSequence()) {
        val idx = line.indexOf(':')
        if (idx > 0) {
            val key = line.substring(0, idx).trim()
            if (key == "model name" || key == "Hardware") {
                return line.substring(idx + 1).trim()
            }
        }
    }
    return "Android CPU"
}

actual fun platformCpuCores(): Pair<Int, Int> {
    val logical = sysconf(_SC_NPROCESSORS_ONLN).toInt().takeIf { it > 0 } ?: 1
    return Pair(logical, logical)
}

actual fun platformTotalMemory(): Long {
    val info = readProc("/proc/meminfo") ?: return 0
    for (line in info.lineSequence()) {
        if (line.startsWith("MemTotal:")) {
            val kb = line.removePrefix("MemTotal:").trim().removeSuffix("kB").trim().toLongOrNull() ?: 0
            return kb * 1024
        }
    }
    return 0
}

actual fun platformOsName(): String {
    val release = readProc("/proc/sys/kernel/osrelease")?.trim().orEmpty()
    return if (release.isNotEmpty()) "Android $release" else "Android"
}

private fun readProc(path: String): String? = NativeFileOps.readTextFile(path)
