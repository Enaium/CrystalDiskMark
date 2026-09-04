@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.crystaldiskmark.platform

import kotlinx.cinterop.*
import platform.darwin.sysctlbyname
import platform.posix.size_tVar

private fun sysctlString(name: String): String? {
    val size = nativeHeap.alloc<size_tVar>()
    try {
        size.value = 0u
        if (sysctlbyname(name, null, size.ptr, null, 0u) != 0) return null
        if (size.value == 0uL) return null
        val buf = nativeHeap.allocArray<ByteVar>(size.value.toInt() + 1)
        try {
            if (sysctlbyname(name, buf, size.ptr, null, 0u) != 0) return null
            return buf.toKString()
        } finally {
            nativeHeap.free(buf)
        }
    } finally {
        nativeHeap.free(size)
    }
}

private fun sysctlLong(name: String): Long {
    val size = nativeHeap.alloc<size_tVar>()
    val value = nativeHeap.alloc<LongVar>()
    try {
        size.value = sizeOf<LongVar>().toULong()
        if (sysctlbyname(name, value.ptr, size.ptr, null, 0u) != 0) return 0
        return value.value
    } finally {
        nativeHeap.free(size)
        nativeHeap.free(value)
    }
}

private fun sysctlInt(name: String): Int {
    val size = nativeHeap.alloc<size_tVar>()
    val value = nativeHeap.alloc<IntVar>()
    try {
        size.value = sizeOf<IntVar>().toULong()
        if (sysctlbyname(name, value.ptr, size.ptr, null, 0u) != 0) return 0
        return value.value
    } finally {
        nativeHeap.free(size)
        nativeHeap.free(value)
    }
}

/** Apple platforms: CPU brand via sysctl, memory/cores via sysctl. */
actual fun platformCpuName(): String = sysctlString("machdep.cpu.brand_string") ?: "Apple CPU"

actual fun platformCpuCores(): Pair<Int, Int> {
    val logical = sysctlInt("hw.logicalcpu").takeIf { it > 0 } ?: sysctlInt("hw.ncpu")
    val physical = sysctlInt("hw.physicalcpu").takeIf { it > 0 } ?: logical
    return Pair(physical, logical)
}

actual fun platformTotalMemory(): Long = sysctlLong("hw.memsize")

/** Apple OS name via sysctl. */
actual fun platformOsName(): String {
    val version = sysctlString("kern.osproductversion")
    return if (version != null) "macOS $version" else "Apple OS"
}
