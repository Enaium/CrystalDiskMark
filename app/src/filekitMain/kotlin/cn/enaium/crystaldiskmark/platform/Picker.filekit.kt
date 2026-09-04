package cn.enaium.crystaldiskmark.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

/** FileKit-backed dialogs: JVM / macOS / iOS / Linux / mingw. */
actual suspend fun pickDirectory(initialPath: String?): String? {
    return try {
        val dir = FileKit.openDirectoryPicker(
            directory = initialPath?.let { io.github.vinceglb.filekit.PlatformFile(it) },
            dialogSettings = FileKitDialogSettings.createDefault(),
        )
        dir?.path
    } catch (e: Exception) {
        null
    }
}

actual suspend fun saveFileDialog(
    suggestedName: String,
    defaultExtension: String,
    allowedExtensions: Set<String>,
): String? {
    return try {
        val file = FileKit.openFileSaver(
            suggestedName = suggestedName,
            defaultExtension = defaultExtension,
            allowedExtensions = allowedExtensions,
        )
        file?.path
    } catch (e: Exception) {
        null
    }
}
