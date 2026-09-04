package cn.enaium.crystaldiskmark.platform

import cn.enaium.crystaldiskmark.DriveInfo
import cn.enaium.sdl.SDL
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * SDL3 dialog API implementations.
 * Source set: sdlDialogsMain (macosX64, linuxX64, linuxArm64, tvos, androidNative).
 */
actual suspend fun pickDirectory(initialPath: String?): String? =
    suspendCancellableCoroutine { cont ->
        SDL.showFolderDialog(
            windowId = null,
            defaultLocation = initialPath,
            allowMultiple = false,
        ) { paths ->
            cont.resume(paths.firstOrNull())
        }
    }

actual suspend fun saveFileDialog(
    suggestedName: String,
    defaultExtension: String,
    allowedExtensions: Set<String>,
): String? = suspendCancellableCoroutine { cont ->
    val filters = listOf(
        cn.enaium.sdl.SDLDialogFileFilter("*.$defaultExtension", defaultExtension)
    )
    SDL.showSaveFileDialog(
        windowId = null,
        filters = filters,
        defaultLocation = suggestedName,
    ) { paths ->
        cont.resume(paths.firstOrNull())
    }
}

/**
 * Fallback drive enumeration without sysinfo-kmp:
 * home dir + system roots.
 */
actual fun listDrives(): List<DriveInfo> {
    val io = platformIo()
    val home = io.homeDir()
    val result = mutableListOf(
        DriveInfo(
            displayName = "Home",
            path = home,
            totalBytes = io.totalSpace(home),
            freeBytes = io.freeSpace(home),
        )
    )
    val root = "/"
    if (root != home && root.isNotEmpty()) {
        result.add(
            DriveInfo(
                displayName = "System",
                path = root,
                totalBytes = io.totalSpace(root),
                freeBytes = io.freeSpace(root),
            )
        )
    }
    return result
}
