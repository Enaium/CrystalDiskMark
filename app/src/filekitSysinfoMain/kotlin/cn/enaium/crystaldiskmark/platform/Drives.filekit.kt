package cn.enaium.crystaldiskmark.platform

import cn.enaium.crystaldiskmark.DriveInfo
import cn.enaium.sysinfo.Disks

/**
 * sysinfo-kmp based drive enumeration.
 * Source set: sysinfoMain (jvm, desktop native, ios, android native).
 */
actual fun listDrives(): List<DriveInfo> {
    val result = mutableListOf<DriveInfo>()
    // User home first, so the test location defaults to a writable path.
    try {
        val io = platformIo()
        val home = io.homeDir()
        if (home.isNotEmpty()) {
            result.add(
                DriveInfo(
                    displayName = "Home",
                    path = home,
                    totalBytes = io.totalSpace(home),
                    freeBytes = io.freeSpace(home),
                )
            )
        }
    } catch (_: Throwable) {
    }
    try {
        val disks = Disks()
        try {
            disks.refresh()
            for (d in disks.list) {
                if (d.mountPoint.isBlank()) continue
                result.add(
                    DriveInfo(
                        displayName = d.mountPoint,
                        path = d.mountPoint,
                        totalBytes = d.totalSpaceBytes.toLong(),
                        freeBytes = d.availableSpaceBytes.toLong(),
                    )
                )
            }
        } finally {
            disks.close()
        }
    } catch (_: Throwable) {
    }
    return result
}
