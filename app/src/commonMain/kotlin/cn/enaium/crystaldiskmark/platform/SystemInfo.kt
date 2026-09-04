package cn.enaium.crystaldiskmark.platform

/** System information snapshot (sysinfo-kmp backed where available). */
data class SystemInfo(
    val osName: String,
    val osVersion: String,
    val cpuName: String,
    val cpuCores: Pair<Int, Int>,
    val memoryBytes: Long,
    val hostName: String,
)

expect fun getSystemInfo(): SystemInfo
