@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import kotlinx.cinterop.*
import platform.posix.*

/** Linux: parse /proc/cpuinfo, /proc/meminfo, /proc/sys/kernel. */
actual fun platformCpuName(): String {
    val info = readProc("/proc/cpuinfo") ?: return "Linux CPU"
    for (line in info.lineSequence()) {
        val idx = line.indexOf(':')
        if (idx > 0 && line.substring(0, idx).trim() == "model name") {
            return line.substring(idx + 1).trim()
        }
    }
    return "Linux CPU"
}

actual fun platformCpuCores(): Pair<Int, Int> {
    val logical = sysconf(_SC_NPROCESSORS_ONLN).toInt().takeIf { it > 0 } ?: 1
    val info = readProc("/proc/cpuinfo")
    var physical = 0
    if (info != null) {
        val seen = HashSet<String>()
        for (line in info.lineSequence()) {
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).trim() == "physical id") {
                seen.add(line.substring(idx + 1).trim())
            }
        }
        physical = seen.size
    }
    return Pair(physical.takeIf { it > 0 } ?: logical, logical)
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

/** Linux OS name: ostype + osrelease from /proc. */
actual fun platformOsName(): String {
    val type = readProc("/proc/sys/kernel/ostype")?.trim().orEmpty()
    val release = readProc("/proc/sys/kernel/osrelease")?.trim().orEmpty()
    return if (type.isNotEmpty()) "$type $release" else "Linux"
}

private fun readProc(path: String): String? {
    val fd = open(path, O_RDONLY)
    if (fd < 0) return null
    try {
        val size = lseek(fd, 0, SEEK_END)
        lseek(fd, 0, SEEK_SET)
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
