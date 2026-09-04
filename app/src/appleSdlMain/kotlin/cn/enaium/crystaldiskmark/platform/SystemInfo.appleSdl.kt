package cn.enaium.crystaldiskmark.platform

/** iOS/tvOS fallback system info (no sysinfo-kmp variant). */
actual fun getSystemInfo(): SystemInfo {
    val io = platformIo()
    return SystemInfo(
        osName = io.osName(),
        osVersion = "",
        cpuName = io.cpuName(),
        cpuCores = io.cpuCores(),
        memoryBytes = io.totalMemory(),
        hostName = "",
    )
}
