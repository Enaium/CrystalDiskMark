package cn.enaium.crystaldiskmark.platform

import cn.enaium.sysinfo.System

/**
 * sysinfo-kmp backed system info.
 * Source set: sysinfoMain (jvm, desktop native, ios, android native).
 */
actual fun getSystemInfo(): SystemInfo {
    val cores = platformIo().cpuCores()
    val cpus = try {
        System(newAll = false).use { s -> s.cpus }
    } catch (_: Throwable) {
        emptyList()
    }
    val cpuName = cpus.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: platformIo().cpuName()

    val memory = try {
        System(newAll = false).use { it.totalMemory.toLong() }
    } catch (_: Throwable) {
        platformIo().totalMemory()
    }

    return SystemInfo(
        osName = System.osVersion() ?: platformIo().osName(),
        osVersion = System.longOsVersion() ?: "",
        cpuName = cpuName,
        cpuCores = cores,
        memoryBytes = memory,
        hostName = System.hostName() ?: "",
    )
}
