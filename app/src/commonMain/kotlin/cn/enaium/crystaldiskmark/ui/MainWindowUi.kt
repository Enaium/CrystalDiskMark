package cn.enaium.crystaldiskmark.ui

import cn.enaium.crystaldiskmark.Fmt
import cn.enaium.crystaldiskmark.*
import cn.enaium.imgui.ImGui
import cn.enaium.imgui.ImGuiCond
import cn.enaium.imgui.ImVec2
import cn.enaium.imgui.ImDrawList
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

/**
 * Main window, layout mirroring the reference CDiskMarkDlg:
 * 480x300 client area (SIZE_X=480, SIZE_Y=300), scaled by zoom.
 *
 *   Menu bar (File/Settings/Profile/Theme/Help/Language)
 *   Row 0: [All btn] [Count] [Size] [Drive] [Unit]
 *   Row 1: [Test0 btn]  Read meter 0  Write meter 0
 *   Row 2: [Test1 btn]  Read meter 1  Write meter 1
 *   Row 3: [Test2 btn]  Read meter 2  Write meter 2
 *   Row 4: [Test3 btn]  Read meter 3  Write meter 3
 */
class MainWindowUi(
    val state: AppState,
    val settings: Settings,
    val fontRegular: cn.enaium.imgui.ImFont,
    val fontLarge: cn.enaium.imgui.ImFont,
    val fontDemo: cn.enaium.imgui.ImFont,
    /** True while a benchmark is running: the All/Test buttons are disabled. */
    val isRunning: () -> Boolean,
    val onAll: () -> Unit,
    val onTest: (Int) -> Unit,
    val onStop: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenFont: () -> Unit,
    val onCopy: () -> Unit,
    val onSaveText: () -> Unit,
    val onSaveImage: () -> Unit,
    val onExit: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onPickFolder: () -> Unit,
    val onDriveChanged: () -> Unit,
    val onUnitChanged: () -> Unit,
    val onProfileChanged: () -> Unit,
    val onBenchmarkChanged: () -> Unit,
    val onThemeChanged: () -> Unit,
    val onZoomChanged: () -> Unit,
    val onLangChanged: () -> Unit,
    val onTestDataChanged: () -> Unit,
    val onPresetChanged: () -> Unit,
) {
    val zoomRatio: Float get() = settings.zoomRatio
    val sizeX: Int get() = ((if (settings.mixMode) 680 else 480) * zoomRatio).toInt()
    val sizeY: Int get() = (272 * zoomRatio).toInt()

    /** Viewport stretch factors (>= 1): window size / reference layout. */
    private var scaleX: Float = 1f
    private var scaleY: Float = 1f

    /**
     * Irregular-screen safe-area insets in layout units
     * (left, right, top, bottom); null = full window. Set by Main from
     * the platform layer (Android punch holes / rounded corners).
     */
    var safeInsets: FloatArray? = null

    /** Menu bar geometry for the current frame (inside the safe area). */
    private var menuX = 0f
    private var menuY = 0f
    private var menuW = 0f

    private val colors = ThemeColors

    // ------------------------------------------------------------------
    // Metrics (mirrors CDiskMarkDlg::SetMeter)
    // ------------------------------------------------------------------
    fun meterRatio(score: Double, unit: Int): Double {
        val ratio = if (unit == ScoreUnit.US.value) {
            // latency-based: smaller latency -> bigger bar
            1.0 - 0.16666666666666 * log10(max(score, 0.0000000001))
        } else {
            if (score > 0.1) 0.16666666666666 * log10(score * 10) else 0.0
        }
        return ratio.coerceIn(0.0, 1.0)
    }

    private fun formatValue(score: Double, latency: Double, blockSize: Int, unit: Int, demo: Boolean): String {
        return when (unit) {
            ScoreUnit.IOPS.value -> {
                val iops = score * 1000 * 1000 / blockSize
                if (demo) {
                    if (iops >= 100000.0) "${(iops / 1000).toInt()}k" else "${iops.toInt()}"
                } else {
                    if (iops >= 1000000.0) "${iops.toInt()}" else Fmt.format("%.2f", iops)
                }
            }
            ScoreUnit.US.value -> {
                if (demo) {
                    if (latency >= 1000000.0) "${(latency / 1000).toInt()}"
                    else if (latency >= 1000.0) "${latency.toInt()}"
                    else Fmt.format("%.1f", latency)
                } else {
                    if (latency >= 1000000.0) "${latency.toInt()}" else Fmt.format("%.2f", latency)
                }
            }
            ScoreUnit.GBS.value -> if (demo) Fmt.format("%.1f", score / 1000) else Fmt.format("%.3f", score / 1000)
            else -> if (demo) {
                if (score >= 1000.0) "${score.toInt()}" else Fmt.format("%.1f", score)
            } else {
                if (score >= 1000000.0) "${score.toInt()}" else Fmt.format("%.2f", score)
            }
        }
    }

    private fun unitLabel(unit: Int): String = when (unit) {
        ScoreUnit.IOPS.value -> "IOPS"
        ScoreUnit.US.value -> "us"
        ScoreUnit.GBS.value -> "GB/s"
        else -> "MB/s"
    }

    // ------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------
    fun draw() {
        // Main window starts below the global menu bar (y = frame height)
        // and spans the rest of the viewport. On irregular screens the
        // content is laid out inside the safe area (punch holes, rounded
        // corners); the surrounding margin stays window background.
        val menuH = ImGui.getFrameHeight()
        val vw = ImGui.getIO().displaySize.x
        val vh = ImGui.getIO().displaySize.y
        val ins = safeInsets
        val inL = (ins?.get(0) ?: 0f).coerceIn(0f, vw * 0.45f)
        val inR = (ins?.get(1) ?: 0f).coerceIn(0f, vw * 0.45f)
        val inT = (ins?.get(2) ?: 0f).coerceIn(0f, (vh - menuH) * 0.45f)
        val inB = (ins?.get(3) ?: 0f).coerceIn(0f, (vh - menuH) * 0.45f)
        val areaX = inL
        val areaY = inT + menuH
        val areaW = (vw - inL - inR).coerceAtLeast(1f)
        val areaH = (vh - menuH - inT - inB).coerceAtLeast(1f)
        // The menu bar is drawn in its own window at the safe-area origin.
        menuX = inL
        menuY = inT
        menuW = areaW
        // Stretch the reference layout to fill the safe area (>= 1x).
        scaleX = (areaW / sizeX.toFloat()).coerceAtLeast(1f)
        scaleY = (areaH / sizeY.toFloat()).coerceAtLeast(1f)
        ImGui.setNextWindowPos(ImVec2(areaX, areaY), ImGuiCond.ALWAYS)
        ImGui.setNextWindowSize(
            ImVec2(areaW, areaH),
            ImGuiCond.ALWAYS,
        )
        if (ImGui.begin(
                "CrystalDiskMark",
                flags = cn.enaium.imgui.ImGuiWindowFlags.NO_TITLE_BAR or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_RESIZE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_MOVE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_SCROLLBAR or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_COLLAPSE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_BRING_TO_FRONT_ON_FOCUS or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_SAVED_SETTINGS
            )
        ) {
            val dl = ImGui.getWindowDrawList()
            // Content origin: where ImGui places widgets at cursor (0,0)
            // (window pos + padding). Drawing at origin + (x,y) therefore
            // lines up exactly with widget hitboxes set via setCursorPos.
            ImGui.setCursorPos(ImVec2(0f, 0f))
            val origin = ImGui.getCursorScreenPos()

            drawCombosAndButtons(dl, origin)
            drawMeters(dl, origin)
        }
        ImGui.end()

        // Global main menu bar: rendered by ImGui at the very top of the
        // viewport, on top of the main window.
        drawMainMenuBar()
    }

    private fun drawMainMenuBar() {
        // The bar lives in its own window so it can be placed inside the
        // irregular-screen safe area (punch holes / rounded corners).
        val topH = ImGui.getFrameHeight()
        ImGui.pushStyleVarVec2(cn.enaium.imgui.ImGuiStyleVar.WINDOW_PADDING, ImVec2(0f, 0f))
        ImGui.pushStyleVarFloat(cn.enaium.imgui.ImGuiStyleVar.WINDOW_ROUNDING, 0f)
        ImGui.pushStyleVarFloat(cn.enaium.imgui.ImGuiStyleVar.WINDOW_BORDER_SIZE, 0f)
        ImGui.setNextWindowPos(ImVec2(menuX, menuY), ImGuiCond.ALWAYS)
        ImGui.setNextWindowSize(ImVec2(menuW, topH), ImGuiCond.ALWAYS)
        if (!ImGui.begin(
                "##topbar",
                flags = cn.enaium.imgui.ImGuiWindowFlags.NO_TITLE_BAR or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_RESIZE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_MOVE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_SCROLLBAR or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_COLLAPSE or
                    cn.enaium.imgui.ImGuiWindowFlags.NO_SAVED_SETTINGS or
                    cn.enaium.imgui.ImGuiWindowFlags.MENU_BAR
            )
        ) {
            ImGui.end()
            ImGui.popStyleVar(3)
            return
        }
        ImGui.pushFont(fontRegular)
        ImGui.beginMenuBar()

        if (ImGui.beginMenu(Lang.t("Menu.FILE"))) {
            if (ImGui.menuItem(Lang.t("Menu.EDIT_COPY"), "Ctrl+Shift+C")) onCopy()
            if (ImGui.menuItem(Lang.t("Menu.SAVE_TEXT"), "Ctrl+T")) onSaveText()
            if (ImGui.menuItem(Lang.t("Menu.SAVE_IMAGE"), "Ctrl+S")) onSaveImage()
            ImGui.separator()
            if (ImGui.menuItem(Lang.t("Menu.FILE_EXIT"), "Alt+F4")) onExit()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(Lang.t("Menu.SETTINGS"))) {
            if (ImGui.beginMenu(Lang.t("Menu.TEST_DATA"))) {
                if (ImGui.menuItem(
                        Lang.t("Menu.DEFAULT_RANDOM"), "",
                        settings.testData == TestData.RANDOM.value
                    )
                ) {
                    settings.testData = TestData.RANDOM.value
                    onTestDataChanged()
                }
                if (ImGui.menuItem(
                        Lang.t("Menu.ALL_ZERO"), "",
                        settings.testData == TestData.ALL_0X00.value
                    )
                ) {
                    settings.testData = TestData.ALL_0X00.value
                    onTestDataChanged()
                }
                ImGui.endMenu()
            }
            ImGui.separator()
            if (ImGui.menuItem(
                    Lang.t("Menu.PROFILE_DEFAULT"), "",
                    settings.isDefaultPreset()
                )
            ) { settings.applyPreset(0); onPresetChanged() }
            if (ImGui.menuItem(
                    Lang.t("Dialog.PROFILE_PEAK_PERFORMANCE"), "",
                    settings.isNvme8Preset()
                )
            ) { settings.applyPreset(1); onPresetChanged() }
            if (ImGui.menuItem(
                    Lang.t("Dialog.PROFILE_DEMO"), "",
                    settings.isFlashMemoryPreset()
                )
            ) { settings.applyPreset(2); onPresetChanged() }
            ImGui.separator()
            if (ImGui.menuItem(Lang.t("Menu.SETTINGS_QUEUESTHREADS"), "Ctrl+Q")) onOpenSettings()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(Lang.t("Menu.PROFILE"))) {
            if (ImGui.menuItem(Lang.t("Menu.PROFILE_DEFAULT"), "", settings.profile == Profile.DEFAULT.value)) {
                settings.profile = Profile.DEFAULT.value
                onProfileChanged()
            }
            if (ImGui.menuItem(Lang.t("Menu.PROFILE_PEAK"), "", settings.profile == Profile.PEAK.value)) {
                settings.profile = Profile.PEAK.value
                onProfileChanged()
            }
            if (ImGui.menuItem(Lang.t("Menu.PROFILE_REAL"), "", settings.profile == Profile.REAL.value)) {
                settings.profile = Profile.REAL.value
                onProfileChanged()
            }
            if (ImGui.menuItem(Lang.t("Menu.PROFILE_DEMO"), "", settings.profile == Profile.DEMO.value)) {
                settings.profile = Profile.DEMO.value
                onProfileChanged()
            }
            if (ImGui.menuItem(Lang.t("Menu.PROFILE_DEFAULT_MIX"), "", settings.profile == Profile.DEFAULT_MIX.value)) {
                settings.profile = Profile.DEFAULT_MIX.value
                onProfileChanged()
            }
            if (ImGui.menuItem(Lang.t("Menu.PROFILE_PEAK_MIX"), "", settings.profile == Profile.PEAK_MIX.value)) {
                settings.profile = Profile.PEAK_MIX.value
                onProfileChanged()
            }
            if (ImGui.menuItem(Lang.t("Menu.PROFILE_REAL_MIX"), "", settings.profile == Profile.REAL_MIX.value)) {
                settings.profile = Profile.REAL_MIX.value
                onProfileChanged()
            }
            ImGui.separator()
            if (ImGui.menuItem(
                    Lang.t("Menu.BENCHMARK_READ_WRITE"), "",
                    settings.benchmark == BenchmarkMode.READ_WRITE.value
                )
            ) { settings.benchmark = BenchmarkMode.READ_WRITE.value; onBenchmarkChanged() }
            if (ImGui.menuItem(
                    Lang.t("Menu.BENCHMARK_READ_ONLY"), "",
                    settings.benchmark == BenchmarkMode.READ_ONLY.value
                )
            ) { settings.benchmark = BenchmarkMode.READ_ONLY.value; onBenchmarkChanged() }
            if (ImGui.menuItem(
                    Lang.t("Menu.BENCHMARK_WRITE_ONLY"), "",
                    settings.benchmark == BenchmarkMode.WRITE_ONLY.value
                )
            ) { settings.benchmark = BenchmarkMode.WRITE_ONLY.value; onBenchmarkChanged() }
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(Lang.t("Menu.THEME"))) {
            if (ImGui.menuItem(Lang.t("Theme.DARK"), "", settings.themeType == 0)) {
                settings.themeType = 0
                onThemeChanged()
            }
            if (ImGui.menuItem(Lang.t("Theme.LIGHT"), "", settings.themeType == 1)) {
                settings.themeType = 1
                onThemeChanged()
            }
            if (ImGui.menuItem(Lang.t("Theme.CLASSIC"), "", settings.themeType == 2)) {
                settings.themeType = 2
                onThemeChanged()
            }
            ImGui.separator()
            if (ImGui.beginMenu(Lang.t("Menu.ZOOM"))) {
                val zooms = listOf(100 to "100%", 125 to "125%", 150 to "150%", 200 to "200%", 250 to "250%", 300 to "300%")
                for ((v, label) in zooms) {
                    if (ImGui.menuItem(label, "", settings.zoomType == v)) {
                        settings.zoomType = v
                        onZoomChanged()
                    }
                }
                if (ImGui.menuItem(Lang.t("Menu.AUTO"), "", settings.zoomType == 0)) {
                    settings.zoomType = 0
                    onZoomChanged()
                }
                ImGui.endMenu()
            }
            if (ImGui.menuItem(Lang.t("Menu.FONT_SETTING"), "Ctrl+F")) onOpenFont()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(Lang.t("Menu.HELP"))) {
            if (ImGui.menuItem(Lang.t("Menu.HELP_ABOUT"))) onOpenAbout()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(Lang.t("Menu.LANGUAGE"))) {
            // A-N / O-Z grouping like the reference.
            val (aN, oZ) = Lang.languages.partition { it.code.first().uppercaseChar() <= 'N' }
            if (ImGui.beginMenu("A-N")) {
                for (l in aN) {
                    if (ImGui.menuItem("${l.displayName}, [${l.code}]", "", Lang.current == l.code)) {
                        Lang.current = l.code
                        onLangChanged()
                    }
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu("O-Z")) {
                for (l in oZ) {
                    if (ImGui.menuItem("${l.displayName}, [${l.code}]", "", Lang.current == l.code)) {
                        Lang.current = l.code
                        onLangChanged()
                    }
                }
                ImGui.endMenu()
            }
            ImGui.endMenu()
        }

        ImGui.endMenuBar()
        ImGui.popFont()
        ImGui.end()
        ImGui.popStyleVar(3)
    }

    // ------------------------------------------------------------------
    // Combo rows + buttons (reference coordinates, * zoomRatio)
    // ------------------------------------------------------------------
    private fun drawCombosAndButtons(dl: ImDrawList, origin: ImVec2) {
        val z = zoomRatio
        val sx = scaleX
        val sy = scaleY
        val btnW = (72 * z * sx).toFloat()
        val btnH = (48 * z * sy).toFloat()

        // ---- Top row combos ----
        // Count combo at (84, 8) w=40; Size at (128, 8) w=80;
        // Drive at (212, 8) w=188; Unit at (404, 8) w=68.
        // Positions and widths stretch with the viewport; font height is
        // left at the zoom size (no text distortion).
        drawCombo(origin, (84 * z * sx).toFloat(), (8 * z * sy).toFloat(), (40 * z * sx).toFloat(), settings.testCountIndex,
            (1..9).map { it.toString() }.toTypedArray()) { settings.testCountIndex = it }

        drawCombo(origin, (128 * z * sx).toFloat(), (8 * z * sy).toFloat(), (80 * z * sx).toFloat(), settings.testSizeIndex,
            Settings.TEST_SIZES) { settings.testSizeIndex = it }

        val driveW = if (settings.profile == Profile.PEAK.value || settings.profile == Profile.REAL.value) {
            (260 * z * sx).toFloat()
        } else {
            (188 * z * sx).toFloat()
        }
        val driveItems = (settings.driveItems + Lang.t("Menu.SELECT_FOLDER")).toTypedArray()
        val prevDriveSel = settings.testDriveIndex
        drawCombo(origin, (212 * z * sx).toFloat(), (8 * z * sy).toFloat(), driveW, prevDriveSel, driveItems) {
            settings.testDriveIndex = it
            if (it == settings.driveItems.size) {
                onPickFolder()
            } else {
                onDriveChanged()
            }
        }
        if (settings.profile == Profile.DEFAULT.value || settings.profile == Profile.DEMO.value) {
            val unitItems = arrayOf("MB/s", "GB/s", "IOPS", "us")
            drawCombo(origin, (404 * z * sx).toFloat(), (8 * z * sy).toFloat(), (68 * z * sx).toFloat(),
                settings.testUnitIndex, unitItems) {
                settings.testUnitIndex = it
                onUnitChanged()
            }
        }
        // Mix ratio combo (reference: R10/W90 .. R90/W10 at x=480).
        if (settings.mixMode) {
            val mixItems = (1..9).map { "R${it * 10}%/W${(10 - it) * 10}%" }.toTypedArray()
            val mixIndex = (9 - settings.mixWriteRatio / 10).coerceIn(0, 8)
            drawCombo(origin, (476 * z * sx).toFloat(), (8 * z * sy).toFloat(), (196 * z * sx).toFloat(),
                mixIndex, mixItems) {
                settings.mixWriteRatio = (9 - it) * 10
            }
        }

        // ---- Buttons ----
        // All button is anchored at the same x as the four test buttons
        // below it (reference: both use x=8), scaled by zoom.
        val allLabel = if (settings.measureTime == 5) "All" else "All\n${settings.measureTime}sec"
        drawButton(dl, origin, 8f * z * sx, 8f * z * sy, btnW, btnH, allLabel, colors.buttonText) { onAll() }

        val profile = settings.profile
        val isPeakReal = profile == Profile.PEAK.value || profile == Profile.REAL.value ||
            profile == Profile.PEAK_MIX.value || profile == Profile.REAL_MIX.value
        val isDemo = profile == Profile.DEMO.value

        if (!isDemo) {
            val btnLabels = buttonLabels()
            for (i in 0 until 4) {
                val y = (60 + i * 52) * z * sy
                drawButton(dl, origin, 8f * z * sx, y, btnW, btnH, btnLabels[i], colors.buttonText) { onTest(i) }
            }
        } else {
            // Demo: single All button only.
        }

        // ---- Unit headers ----
        val readUnit = if (isPeakReal) "Read (MB/s)" else "Read (${unitLabel(settings.testUnitIndex)})"
        val writeUnit = if (isPeakReal) "Write (MB/s)" else "Write (${unitLabel(settings.testUnitIndex)})"
        drawText(dl, origin, (84 * z * sx).toFloat(), (36 * z * sy).toFloat(), readUnit, colors.labelText, 16 * z)
        drawText(dl, origin, (280 * z * sx).toFloat(), (36 * z * sy).toFloat(), writeUnit, colors.labelText, 16 * z)
        if (settings.mixMode) {
            drawText(dl, origin, (476 * z * sx).toFloat(), (36 * z * sy).toFloat(), "Mix (MB/s)", colors.labelText, 16 * z)
        }
    }

    private fun buttonLabels(): Array<String> {
        val z = zoomRatio
        val profile = settings.profile
        val unit = settings.testUnitIndex
        return if (profile == Profile.PEAK.value || profile == Profile.PEAK_MIX.value) {
            val s4 = settings.slots[4]; val s5 = settings.slots[5]
            arrayOf(
                buttonText(s4, ScoreUnit.MBS.value),
                buttonText(s5, ScoreUnit.MBS.value),
                buttonText(s5, ScoreUnit.IOPS.value),
                buttonText(s5, ScoreUnit.US.value),
            )
        } else if (profile == Profile.REAL.value || profile == Profile.REAL_MIX.value) {
            arrayOf(
                buttonText(BenchSetting(0, 1024, 1, 1), ScoreUnit.MBS.value),
                buttonText(BenchSetting(1, 4, 1, 1), ScoreUnit.MBS.value),
                buttonText(BenchSetting(1, 4, 1, 1), ScoreUnit.IOPS.value),
                buttonText(BenchSetting(1, 4, 1, 1), ScoreUnit.US.value),
            )
        } else {
            arrayOf(
                buttonText(settings.slots[0], unit),
                buttonText(settings.slots[1], unit),
                buttonText(settings.slots[2], unit),
                buttonText(settings.slots[3], unit),
            )
        }
    }

    private fun buttonText(s: BenchSetting, unit: Int): String {
        val prefix = if (s.type == BenchType.RND.value) "RND" else "SEQ"
        val sizeStr = if (s.size >= 1024) "${s.size / 1024}M" else "${s.size}K"
        return when (unit) {
            ScoreUnit.IOPS.value -> "$prefix$sizeStr\n(IOPS)"
            ScoreUnit.US.value -> "$prefix$sizeStr\n(us)"
            else -> "$prefix$sizeStr\nQ${s.queues}T${s.threads}"
        }
    }

    // ------------------------------------------------------------------
    // Meters
    // ------------------------------------------------------------------
    private fun drawMeters(dl: ImDrawList, origin: ImVec2) {
        val z = zoomRatio
        val sx = scaleX
        val sy = scaleY
        val profile = settings.profile
        val isDemo = profile == Profile.DEMO.value
        val isPeak = profile == Profile.PEAK.value || profile == Profile.PEAK_MIX.value
        val isReal = profile == Profile.REAL.value || profile == Profile.REAL_MIX.value

        val mw = (192 * z * sx).toFloat()
        val mh = (48 * z * sy).toFloat()
        val rx = (84 * z * sx).toFloat()
        val wx = (280 * z * sx).toFloat()
        val mx = (476 * z * sx).toFloat()

        if (isDemo) {
            // Demo: one big read + one big write meter, plus a "Demo"
            // settings label at (84, 36).
            drawMeter(dl, origin, rx, (64 * z * sy).toFloat(), (228 * z * sx).toFloat(), (196 * z * sy).toFloat(),
                ScoreUnit.MBS.value, state.readScore[8].score, state.readScore[8].latency,
                settings.slots[8], "Read", colors.readMeter, demo = true)
            drawMeter(dl, origin, wx, (64 * z * sy).toFloat(), (228 * z * sx).toFloat(), (196 * z * sy).toFloat(),
                ScoreUnit.MBS.value, state.writeScore[8].score, state.writeScore[8].latency,
                settings.slots[8], "Write", colors.writeMeter, demo = true)
            val demoText = demoSettingText()
            drawText(dl, origin, (84 * z * sx).toFloat(), (36 * z * sy).toFloat(), demoText, colors.labelText, 16 * z)
            return
        }

        val unit = settings.testUnitIndex
        for (i in 0 until 4) {
            val y = (60 + i * 52) * z * sy
            val (slot, slotUnit, label) = when {
                isPeak -> when (i) {
                    0 -> Triple(4, ScoreUnit.MBS.value, meterLabel(settings.slots[4], ScoreUnit.MBS.value))
                    1 -> Triple(4, ScoreUnit.MBS.value, meterLabel(settings.slots[4], ScoreUnit.MBS.value))
                    2 -> Triple(5, ScoreUnit.IOPS.value, meterLabel(settings.slots[5], ScoreUnit.IOPS.value))
                    else -> Triple(5, ScoreUnit.US.value, meterLabel(settings.slots[5], ScoreUnit.US.value))
                }
                isReal -> when (i) {
                    0 -> Triple(6, ScoreUnit.MBS.value, meterLabel(settings.slots[6], ScoreUnit.MBS.value))
                    1 -> Triple(6, ScoreUnit.MBS.value, meterLabel(settings.slots[6], ScoreUnit.MBS.value))
                    2 -> Triple(7, ScoreUnit.IOPS.value, meterLabel(settings.slots[7], ScoreUnit.IOPS.value))
                    else -> Triple(7, ScoreUnit.US.value, meterLabel(settings.slots[7], ScoreUnit.US.value))
                }
                else -> Triple(i, unit, meterLabel(settings.slots[i], unit))
            }

            drawMeter(dl, origin, rx, y, mw, mh, slotUnit,
                state.readScore[slot].score, state.readScore[slot].latency,
                settings.slots[slot], label, colors.readMeter, demo = false)
            drawMeter(dl, origin, wx, y, mw, mh, slotUnit,
                state.writeScore[slot].score, state.writeScore[slot].latency,
                settings.slots[slot], label, colors.writeMeter, demo = false)
            if (settings.mixMode) {
                drawMeter(dl, origin, mx, y, mw, mh, slotUnit,
                    state.mixScore[slot].score, state.mixScore[slot].latency,
                    settings.slots[slot], label, colors.mixMeter, demo = false)
            }
        }
    }

    private fun meterLabel(s: BenchSetting, unit: Int): String {
        val prefix = if (s.type == BenchType.RND.value) "RND" else "SEQ"
        val sizeStr = if (s.size >= 1024) "${s.size / 1024}MiB" else "${s.size}KiB"
        return when (unit) {
            ScoreUnit.IOPS.value -> "$prefix $sizeStr\n(IOPS)"
            ScoreUnit.US.value -> "$prefix $sizeStr\n(us)"
            else -> "$prefix $sizeStr\nQ${s.queues} T${s.threads}"
        }
    }

    private fun demoSettingText(): String {
        val s = settings.slots[8]
        val type = if (s.type == BenchType.RND.value) "RND" else "SEQ"
        val size = if (s.size >= 1024) "${s.size / 1024}MiB" else "${s.size}KiB"
        return "$type $size, Q=${s.queues}, T=${s.threads}"
    }

    private fun drawMeter(
        dl: ImDrawList,
        origin: ImVec2,
        x: Float, y: Float, w: Float, h: Float,
        unit: Int,
        score: Double, latency: Double,
        slot: BenchSetting,
        label: String,
        meterColor: Int,
        demo: Boolean,
    ) {
        // Panel aligned with the widget content area (origin is the
        // cursor screen pos at (0,0), which includes window padding).
        // x/y/w/h already carry the viewport stretch (scaleX/scaleY).
        val padX = 4f * zoomRatio * scaleX
        val padY = 4f * zoomRatio * scaleY
        val p0 = ImVec2(origin.x + x, origin.y + y)
        val p1 = ImVec2(origin.x + x + w, origin.y + y + h)
        // Panel background
        dl.DrawRectFilled(p0, p1, colors.meterBk, 4f * zoomRatio)
        dl.DrawRect(p0, p1, colors.meterFrame, 4f * zoomRatio, 0, 1f)
        // Ratio bar
        val ratio = meterRatio(score, unit)
        if (ratio > 0.01) {
            val bx = ImVec2(p0.x + padX, p0.y + padY)
            val bw = ((w - 2f * padX) * ratio.toFloat())
            val by = ImVec2(bx.x + bw, p0.y + h - padY)
            dl.DrawRectFilled(bx, by, meterColor, 2f * zoomRatio)
        }
        // Value text (right-aligned), using the large/demo font.
        val blockSize = slot.size * 1024
        val value = formatValue(score, latency, blockSize, unit, demo)
        // Demo meters have generous height: use the demo font. Regular
        // meters are 48*z tall: the value font (20*z, ~40px at 200%) sits
        // at the top, the two-line label (14*z) at the bottom; together
        // they fit the panel height (value 40 + label 2*28 = 96px).
        // Text stays at the zoom size; only its anchor stretches.
        ImGui.pushFont(if (demo) fontDemo else fontLarge)
        val textW = ImGui.calcTextSize(value).x
        dl.DrawText(ImVec2(p1.x - textW - padX, p0.y + padY), value, colors.meterText)
        ImGui.popFont()
        // Label text (left-aligned) in the regular font, bottom-aligned
        // inside the panel. Single line so the value above it fits the
        // 48*z panel height.
        ImGui.pushFont(fontRegular)
        val oneLine = label.replace("\n", " ")
        val labelH = ImGui.calcTextSize(oneLine).y
        dl.DrawText(ImVec2(p0.x + 2f * padX, p1.y - labelH - padY), oneLine, colors.meterLabelText)
        ImGui.popFont()
    }

    // ------------------------------------------------------------------
    // Widget helpers
    // ------------------------------------------------------------------
    private fun drawCombo(
        origin: ImVec2,
        x: Float, y: Float, w: Float,
        currentIndex: Int,
        items: Array<String>,
        onChange: (Int) -> Unit,
    ) {
        ImGui.setCursorPos(ImVec2(x, y))
        ImGui.setNextItemWidth(w)
        val current = IntArray(1) { currentIndex.coerceIn(0, items.size - 1) }
        if (ImGui.combo("##combo$x$y", current, items)) {
            onChange(current[0])
        }
    }

    private fun drawButton(
        dl: ImDrawList,
        origin: ImVec2,
        x: Float, y: Float, w: Float, h: Float,
        label: String,
        textColor: Int,
        onClick: () -> Unit,
    ) {
        val running = isRunning()
        ImGui.setCursorPos(ImVec2(x, y))
        val p0 = ImGui.getCursorScreenPos()
        if (running) {
            ImGui.beginDisabled()
        }
        if (ImGui.button("##btn$x$y", ImVec2(w, h))) {
            onClick()
        }
        if (running) {
            ImGui.endDisabled()
        }
        // Draw the label centered over the button, aligned with the
        // button's hitbox (cursor screen pos, which includes window
        // padding). The font is already scaled by zoom, so calcTextSize
        // returns the final pixel width — do NOT multiply by zoomRatio
        // again (that overflowed the button at 200%).
        val lines = label.split("\n")
        val lineH = 18f * zoomRatio
        val totalH = lines.size * lineH
        var ty = p0.y + (h - totalH) / 2f
        val color = if (running) colors.disabledText else textColor
        for (line in lines) {
            val tw = ImGui.calcTextSize(line).x
            dl.DrawText(ImVec2(p0.x + (w - tw) / 2f, ty), line, color)
            ty += lineH
        }
    }

    private fun drawText(
        dl: ImDrawList,
        origin: ImVec2,
        x: Float, y: Float,
        text: String,
        color: Int,
        size: Float,
    ) {
        dl.DrawText(ImVec2(origin.x + x, origin.y + y), text, color)
    }
}

/**
 * Custom draw-list colors. The values switch with the imgui preset theme
 * (Dark / Light / Classic) so manually drawn text stays readable on the
 * widget backgrounds of either theme.
 */
object ThemeColors {
    var labelText: Int = 0xFFE0E0E0.toInt()
    var buttonText: Int = 0xFFE8E8E8.toInt()
    var disabledText: Int = 0xFF707070.toInt()
    var meterText: Int = 0xFFF0F0F0.toInt()
    var meterLabelText: Int = 0xFFB0B0B0.toInt()
    val meterBk: Int = 0x1FFFFFFF.toInt()
    val meterFrame: Int = 0xFF606060.toInt()

    /** Dark preset (default): light text on dark widgets. */
    fun setDark() {
        labelText = 0xFFE0E0E0.toInt()
        buttonText = 0xFFE8E8E8.toInt()
        disabledText = 0xFF707070.toInt()
        meterText = 0xFFF0F0F0.toInt()
        meterLabelText = 0xFFB0B0B0.toInt()
    }

    /** Light preset: dark text on light widgets. */
    fun setLight() {
        labelText = 0xFF1E1E1E.toInt()
        buttonText = 0xFF202020.toInt()
        disabledText = 0xFF909090.toInt()
        meterText = 0xFF181818.toInt()
        meterLabelText = 0xFF505050.toInt()
    }

    /** Classic preset: white text on gray widgets (readable against the
     *  classic gray window background). */
    fun setClassic() {
        labelText = 0xFFE0E0E0.toInt()
        buttonText = 0xFFE8E8E8.toInt()
        disabledText = 0xFF909090.toInt()
        meterText = 0xFFF0F0F0.toInt()
        meterLabelText = 0xFFB0B0B0.toInt()
    }
    val readMeter: Int = 0xFF4F8FDF.toInt()
    val writeMeter: Int = 0xFFDF8F4F.toInt()
    val mixMeter: Int = 0xFF6FBF6F.toInt()
}
