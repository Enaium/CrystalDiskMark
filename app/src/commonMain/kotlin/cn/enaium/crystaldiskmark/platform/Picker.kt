package cn.enaium.crystaldiskmark.platform

import cn.enaium.crystaldiskmark.DriveInfo

/**
 * Directory picking abstraction.
 * - filekitMain (JVM/macOS/iOS/Linux/mingw): FileKit native dialogs
 * - androidMain: SDL folder dialog (SAF)
 * - tvosMain: unsupported, returns null (app container is the target)
 */
expect suspend fun pickDirectory(initialPath: String?): String?

/**
 * Save-file dialog abstraction.
 * - filekitMain: FileKit save dialog
 * - androidMain: SDL save dialog
 * - tvosMain: unsupported, returns null
 */
expect suspend fun saveFileDialog(
    suggestedName: String,
    defaultExtension: String,
    allowedExtensions: Set<String>,
): String?

/** Enumerates benchmark target volumes. */
expect fun listDrives(): List<DriveInfo>
