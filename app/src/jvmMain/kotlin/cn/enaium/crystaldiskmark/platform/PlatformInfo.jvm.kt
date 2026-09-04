package cn.enaium.crystaldiskmark.platform

import java.io.File

actual fun platformOsName(): String = JvmPlatformIo.osName()

actual fun platformFreeSpace(path: String): Long = File(path).usableSpace

actual fun platformTotalSpace(path: String): Long = File(path).totalSpace

actual fun platformCpuName(): String = JvmPlatformIo.cpuName()

actual fun platformCpuCores(): Pair<Int, Int> = JvmPlatformIo.cpuCores()

actual fun platformTotalMemory(): Long = JvmPlatformIo.totalMemory()
