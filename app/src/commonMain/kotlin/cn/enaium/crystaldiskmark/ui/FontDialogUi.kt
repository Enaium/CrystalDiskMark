package cn.enaium.crystaldiskmark.ui

import cn.enaium.crystaldiskmark.Lang
import cn.enaium.crystaldiskmark.Settings
import cn.enaium.imgui.ImGui
import cn.enaium.imgui.ImGuiCond
import cn.enaium.imgui.ImVec2

/**
 * Font Setting dialog, mirroring CFontSelectionDlg:
 * Font Face / Font Scale / Render Method + OK.
 */
class FontDialogUi(
    private val settings: Settings,
    private val onClose: () -> Unit,
) {
    private var open = false

    fun open() {
        open = true
    }

    fun draw() {
        if (!open) return
        ImGui.setNextWindowSize(ImVec2(260f, 180f), ImGuiCond.ALWAYS)
        val openRef = BooleanArray(1) { open }
        if (!ImGui.begin(
                Lang.t("Menu.FONT_SETTING"),
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

        // Font Face
        ImGui.text("Font Face")
        val faces = arrayOf("System Default", "Noto Sans CJK", "PingFang SC", "Microsoft YaHei", "WenQuanYi Micro Hei")
        val faceIdx = IntArray(1) { 0 }
        if (ImGui.combo("##fontface", faceIdx, faces)) {
            // Face selection is informational; the actual font is resolved
            // by findSystemFont() at startup.
        }

        // Font Scale
        ImGui.text("Font Scale")
        val scales = (50..200 step 10).map { "$it%" }.toTypedArray()
        val scaleIdx = IntArray(1) { ((settings.fontScale - 50) / 10).coerceIn(0, scales.size - 1) }
        if (ImGui.combo("##fontscale", scaleIdx, scales)) {
            settings.fontScale = 50 + scaleIdx[0] * 10
        }

        // Render Method
        ImGui.text("Render Method")
        val renders = arrayOf("Anti-Aliased", "ClearType", "Monochrome")
        val renderIdx = IntArray(1) { 0 }
        ImGui.combo("##fontrender", renderIdx, renders)

        ImGui.separator()

        if (ImGui.button("OK")) {
            settings.save()
            open = false
            onClose()
        }

        ImGui.end()
        if (!openRef[0]) open = false
    }

    fun close() {
        open = false
    }
}
