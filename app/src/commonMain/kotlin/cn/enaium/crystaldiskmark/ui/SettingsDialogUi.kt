package cn.enaium.crystaldiskmark.ui

import cn.enaium.crystaldiskmark.*
import cn.enaium.imgui.ImGui
import cn.enaium.imgui.ImGuiCond
import cn.enaium.imgui.ImVec2

/**
 * Queues & Threads settings dialog, layout mirroring CSettingsDlg:
 * rows for slots 0-5 and 8 (Type/Size/Queues/Threads), preset
 * buttons (Default / Peak / Demo), Measure/Interval time, OK.
 */
class SettingsDialogUi(
    private val settings: Settings,
    private val onClose: () -> Unit,
) {
    private var open = false
    private val measureTimes = Settings.MEASURE_TIMES.map { it.toString() }.toTypedArray()
    private val intervalTimes = Settings.INTERVAL_TIMES.map { it.toString() }.toTypedArray()
    private val queueValues = Settings.QUEUE_VALUES.map { it.toString() }.toTypedArray()
    private val blockSizes = Settings.BLOCK_SIZES.map {
        if (it >= 1024) "${it / 1024}MiB" else "${it}KiB"
    }.toTypedArray()

    fun open() {
        open = true
    }

    fun draw() {
        if (!open) return
        ImGui.setNextWindowSize(ImVec2(440f, 360f), ImGuiCond.ALWAYS)
        val openRef = BooleanArray(1) { open }
        if (!ImGui.begin(
                Lang.t("WindowTitle.SETTINGS"),
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

        // Rows 0..6 -> slots 0,1,2,3,4,5,8
        val rows = listOf(0, 1, 2, 3, 4, 5, 6)
        val colW = floatArrayOf(110f, 90f, 90f, 90f)

        // Header
        ImGui.text(Lang.t("Dialog.TYPE"))
        ImGui.sameLine(colW[0])
        ImGui.text(Lang.t("Dialog.BLOCK_SIZE"))
        ImGui.sameLine(colW[0] + colW[1])
        ImGui.text(Lang.t("Dialog.QUEUES"))
        ImGui.sameLine(colW[0] + colW[1] + colW[2])
        ImGui.text(Lang.t("Dialog.THREADS"))

        for (row in rows) {
            val setting = settings.slots[row]
            val typeItems = arrayOf("SEQ", "RND")

            val typeIdx = IntArray(1) { setting.type }
            if (ImGui.combo("##t$row", typeIdx, typeItems)) {
                setting.type = typeIdx[0]
            }

            ImGui.sameLine(colW[0])
            val sizeIdx = IntArray(1) { Settings.BLOCK_SIZES.indexOf(setting.size).coerceAtLeast(0) }
            if (ImGui.combo("##s$row", sizeIdx, blockSizes)) {
                setting.size = Settings.BLOCK_SIZES[sizeIdx[0]]
            }

            ImGui.sameLine(colW[0] + colW[1])
            val queueIdx = IntArray(1) { Settings.QUEUE_VALUES.indexOf(setting.queues).coerceAtLeast(0) }
            if (ImGui.combo("##q$row", queueIdx, queueValues)) {
                setting.queues = Settings.QUEUE_VALUES[queueIdx[0]]
            }

            ImGui.sameLine(colW[0] + colW[1] + colW[2])
            val threadIdx = IntArray(1) { (setting.threads - 1).coerceIn(0, 63) }
            val threadItems = (1..64).map { it.toString() }.toTypedArray()
            if (ImGui.combo("##th$row", threadIdx, threadItems)) {
                setting.threads = threadIdx[0] + 1
            }
        }

        ImGui.separator()

        // Measure / Interval time
        ImGui.text(Lang.t("Dialog.MEASURE_TIME"))
        ImGui.sameLine(140f)
        val measureIdx = IntArray(1) { Settings.MEASURE_TIMES.indexOf(settings.measureTime).coerceAtLeast(0) }
        if (ImGui.combo("##measure", measureIdx, measureTimes)) {
            settings.measureTime = Settings.MEASURE_TIMES[measureIdx[0]]
        }

        ImGui.text(Lang.t("Dialog.INTERVAL_TIME"))
        ImGui.sameLine(140f)
        val intervalIdx = IntArray(1) { Settings.INTERVAL_TIMES.indexOf(settings.intervalTime).coerceAtLeast(0) }
        if (ImGui.combo("##interval", intervalIdx, intervalTimes)) {
            settings.intervalTime = Settings.INTERVAL_TIMES[intervalIdx[0]]
        }

        ImGui.separator()

        // Preset buttons
        if (ImGui.button(Lang.t("Dialog.DEFAULT"))) {
            settings.applyPreset(0)
        }
        ImGui.sameLine()
        if (ImGui.button(Lang.t("Dialog.PROFILE_PEAK_PERFORMANCE"))) {
            settings.applyPreset(1)
        }
        ImGui.sameLine()
        if (ImGui.button(Lang.t("Dialog.PROFILE_DEMO"))) {
            settings.applyPreset(2)
        }
        ImGui.sameLine()
        if (ImGui.button("OK")) {
            open = false
            onClose()
        }

        ImGui.end()
        if (!openRef[0]) open = false
    }

    fun close() {
        open = false
    }

    val isOpen: Boolean get() = open
}
