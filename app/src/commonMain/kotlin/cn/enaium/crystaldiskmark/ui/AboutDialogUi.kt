package cn.enaium.crystaldiskmark.ui

import cn.enaium.crystaldiskmark.Fmt
import cn.enaium.crystaldiskmark.Lang
import cn.enaium.crystaldiskmark.platform.getSystemInfo
import cn.enaium.imgui.ImGui
import cn.enaium.imgui.ImGuiCond
import cn.enaium.imgui.ImVec2

/** About dialog: version info + system info (sysinfo-kmp backed). */
class AboutDialogUi(
    private val onClose: () -> Unit,
    private val onOpenUrl: (String) -> Unit,
) {
    private var open = false
    private var systemInfo: String = ""

    fun open() {
        open = true
        val info = try {
            getSystemInfo()
        } catch (e: Exception) {
            null
        }
        systemInfo = if (info != null) {
            val cores = info.cpuCores
            val memGiB = info.memoryBytes / 1024.0 / 1024.0 / 1024.0
            buildString {
                append("OS: ").append(info.osName).append('\n')
                if (info.osVersion.isNotBlank()) append("  ").append(info.osVersion).append('\n')
                if (info.hostName.isNotBlank()) append("Host: ").append(info.hostName).append('\n')
                append("CPU: ").append(info.cpuName).append('\n')
                append("Cores: ").append(cores.first).append(" physical / ").append(cores.second).append(" logical\n")
                append(Fmt.format("Memory: %.2f GiB", memGiB))
            }
        } else {
            ""
        }
    }

    fun close() {
        open = false
    }

    fun draw() {
        if (!open) return
        ImGui.setNextWindowSize(ImVec2(420f, 300f), ImGuiCond.ALWAYS)
        val openRef = BooleanArray(1) { open }
        if (!ImGui.begin(
                Lang.t("WindowTitle.ABOUT"),
                openRef,
                flags = cn.enaium.imgui.ImGuiWindowFlags.NO_COLLAPSE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_RESIZE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_SAVED_SETTINGS
            )
        ) {
            ImGui.end()
            if (!openRef[0]) open = false
            return
        }

        ImGui.text("CrystalDiskMark 9.0.3")
        ImGui.text("Kotlin Multiplatform Edition")
        ImGui.text("(C) 2007-2026 hiyohiyo")
        ImGui.text("MIT License")
        ImGui.separator()
        if (ImGui.button("Crystal Dew World")) {
            onOpenUrl("https://crystalmark.info/")
        }
        ImGui.separator()
        if (systemInfo.isNotEmpty()) {
            ImGui.textWrapped(systemInfo)
        } else {
            ImGui.text("(system info unavailable)")
        }
        ImGui.separator()
        if (ImGui.button("Close")) {
            close()
        }
        ImGui.end()
        if (!openRef[0]) open = false
    }

    val isOpen: Boolean get() = open
}
