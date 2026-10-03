package cn.enaium.crystaldiskmark

import cn.enaium.crystaldiskmark.engine.BenchmarkEngine
import cn.enaium.crystaldiskmark.platform.*
import cn.enaium.crystaldiskmark.ui.AboutDialogUi
import cn.enaium.crystaldiskmark.ui.FontDialogUi
import cn.enaium.crystaldiskmark.ui.MainWindowUi
import cn.enaium.crystaldiskmark.ui.SettingsDialogUi
import cn.enaium.imgui.ImFontConfig
import cn.enaium.imgui.ImFontGlyphRanges
import cn.enaium.imgui.ImFontGlyphRangesBuilder
import cn.enaium.imgui.ImGui
import cn.enaium.imgui.ImGuiCol
import cn.enaium.imgui.ImVec4
import cn.enaium.imgui.backends.sdl.ImGuiSdlBackend
import cn.enaium.imgui.backends.sdl.ImGuiSdlRendererBackend
import cn.enaium.sdl.*
import kotlinx.coroutines.runBlocking
import kotlin.math.max

/**
 * CrystalDiskMark KMP main loop.
 * Structure mirrors SysInfoMonitor's runSystemMonitor: SDL window +
 * SDL renderer + ImGui context, frame loop pumping SDL events.
 */
fun runCrystalDiskMark(
    width: Int = 480,
    height: Int = 300,
    maxFrames: Int = Int.MAX_VALUE,
) {
    val io = platformIo()
    SDL.setMainReady()
    if (!SDL.init(SDLInitFlags.VIDEO or SDLInitFlags.EVENTS)) {
        SDL.setHint("SDL_VIDEO_DRIVER", "dummy")
        if (!SDL.init(SDLInitFlags.VIDEO or SDLInitFlags.EVENTS)) {
            error("SDL_Init failed: ${SDL.error()}")
        } else {
            io.log("video init fell back to the dummy driver (headless)")
        }
    }

    val store = ConfigStore(io)
    val settings = Settings(store)
    settings.load()
    Lang.current = store.get("Setting", "Language", "en").let { code ->
        if (Lang.languages.any { it.code == code }) code else "en"
    }
    try {
        settings.refreshDrives()
    } catch (e: Throwable) {
        io.log("drive refresh failed: $e")
    }

    val state = AppState()
    val engine = BenchmarkEngine(io, settings.engineConfig(), object : BenchmarkEngine.Listener {
        override fun onStatus(message: String) {
            statusMessage = message
        }

        override fun onSlotScore(kind: BenchmarkEngine.ScoreKind, slot: Int, score: Double, latency: Double) {
            val target = when (kind) {
                BenchmarkEngine.ScoreKind.READ -> state.readScore[slot]
                BenchmarkEngine.ScoreKind.WRITE -> state.writeScore[slot]
                BenchmarkEngine.ScoreKind.MIX -> state.mixScore[slot]
            }
            target.score = score
            target.latency = latency
            scoreDirty = true
        }
    })

    // Mix profile widens the window (reference SIZE_X_MIX=680).
    // The default zoom scales the whole layout up so the widgets are
    // comfortable and the fixed font sizes fit inside them.
    val zr = settings.zoomRatio
    val initialWidth = ((if (settings.mixMode) 680 else 480) * zr).toInt()
    // SDL window height = content (272*z) + menu bar; the first frame
    // corrects it to the exact menu bar height. Content bottom is the
    // last meter row (y=216*z + 48*z height = 264*z) plus padding.
    val initialHeight = (272 * zr).toInt() + 24
    // Android: a resizable SDL window requests FULL_USER orientation,
    // which follows the device rotation lock (and can leave the app in
    // portrait). A landscape-only hint makes SDL request a fixed
    // landscape orientation instead (SDLActivity.setOrientationBis).
    if (SDL.getCurrentVideoDriver() == "android") {
        SDL.setHint("SDL_ORIENTATIONS", "LandscapeLeft")
    }
    val window = SDL.createWindow(
        title = "CrystalDiskMark",
        width = initialWidth,
        height = initialHeight,
        flags = SDLWindowFlags.HIGH_PIXEL_DENSITY or SDLWindowFlags.RESIZABLE,
    )

    val renderer = SDL.createRenderer(window)
    val context = ImGui.createContext()
    val platform = ImGuiSdlBackend(window)
    val backend = ImGuiSdlRendererBackend(renderer)
    platform.init()

    // Apply the persisted imgui preset theme (Dark / Light / Classic).
    applyTheme(settings.themeType)


    // ---- fonts ----
    val fbScale = (window.sizeInPixels.x.toFloat() / max(window.size.x, 1).toFloat()).coerceAtLeast(1f)
    // Cap the rasterizer density: with the compact glyph set (~600
    // codepoints x 3 font sizes) an unbounded density blows the atlas
    // past 8192x8192 (256MB) on Retina, which drops glyphs on GPUs with
    // smaller texture limits and renders '?'. 1.25x keeps text crisp
    // while keeping the atlas inside 4096x4096 on all platforms.
    val rasterDensity = minOf(fbScale, 1.25f)
    val fonts = ImGui.getIO().fonts
    val fontPath = findSystemFont()
    // Fonts scale with the zoom ratio, mirroring the reference (CDM
    // multiplies its font sizes by m_ZoomRatio). Base sizes at 100%:
    // regular 16, meter value 28 (the value sits above the one-line
    // label inside the 48px meter), demo 44. At the default 200% zoom
    // that renders 32/56/88 logical px.
    //
    // Load only the glyphs the UI can actually display: the characters in
    // all bundled translations plus ASCII/Latin-1 and CJK punctuation.
    // Loading the full CJK ideograph block (~21k glyphs per font) balloons
    // the atlas past GPU texture limits on high-DPI displays and drops
    // glyphs (renders as '?'). Compact ranges keep the atlas ~2048x2048.
    val glyphRanges = ImFontGlyphRangesBuilder()
        .addText(Lang.allTranslatedChars().joinToString(""))
        .addRanges(ImFontGlyphRanges.default)
        .addRanges(intArrayOf(0x3000, 0x303F)) // CJK punctuation
        .addChar('\n'.code)
        .addChar('\t'.code)
        .buildRanges()
    val fontRegular: cn.enaium.imgui.ImFont
    val fontLarge: cn.enaium.imgui.ImFont
    val fontDemo: cn.enaium.imgui.ImFont
    if (fontPath != null) {
        // CJK-capable font (Noto CJK / PingFang / MSYH ...).
        fontRegular = fonts.addFontFromFileTTF(
            fontPath,
            ImFontConfig(sizePixels = 16f * zr, rasterizerDensity = rasterDensity, glyphRanges = glyphRanges),
        )
        fontLarge = fonts.addFontFromFileTTF(
            fontPath,
            ImFontConfig(sizePixels = 28f * zr, rasterizerDensity = rasterDensity, glyphRanges = glyphRanges),
        )
        fontDemo = fonts.addFontFromFileTTF(
            fontPath,
            ImFontConfig(sizePixels = 44f * zr, rasterizerDensity = rasterDensity, glyphRanges = glyphRanges),
        )
    } else {
        fontRegular = fonts.addFontDefault(
            ImFontConfig(sizePixels = 16f * zr, rasterizerDensity = rasterDensity),
        )
        fontLarge = fonts.addFontDefault(
            ImFontConfig(sizePixels = 28f * zr, rasterizerDensity = rasterDensity),
        )
        fontDemo = fonts.addFontDefault(
            ImFontConfig(sizePixels = 44f * zr, rasterizerDensity = rasterDensity),
        )
    }
    check(fonts.build()) { "font atlas build failed" }
    val texData = fonts.getTexDataAsRGBA32()
    val fontTextureId = backend.uploadFontTexture(texData.pixels, texData.width, texData.height)
    fonts.setTexID(fontTextureId)



    // ---- UI objects ----
    var folderPickPending = false

    val aboutDialog = AboutDialogUi(
        onClose = {},
        onOpenUrl = { url -> SDL.openURL(url) },
    )

    val fontDialog = FontDialogUi(
        settings = settings,
        onClose = {},
    )

    val settingsDialog = SettingsDialogUi(
        settings = settings,
        onClose = {
            settings.save()
            settings.refreshDrives()
        },
    )

    val mainUi = MainWindowUi(
        state = state,
        settings = settings,
        fontRegular = fontRegular,
        fontLarge = fontLarge,
        fontDemo = fontDemo,
        isRunning = { benchThread != null },
        onAll = {
            if (benchThread == null) {
                state.resetAll()
                startBenchmark(engine, settings)
                if (clickTest) clickTestResult = true
            } else {
                stopBenchmark(engine)
            }
        },
        onTest = { index ->
            if (benchThread == null) {
                startSlotBenchmark(engine, settings, index)
            } else {
                stopBenchmark(engine)
            }
        },
        onStop = { stopBenchmark(engine) },
        onOpenSettings = { settingsDialog.open() },
        onOpenAbout = { aboutDialog.open() },
        onOpenFont = { fontDialog.open() },
        onCopy = { copyText(settings, state, io) },
        onSaveText = { runBlocking { saveText(settings, state, io) } },
        onSaveImage = { runBlocking { saveImage(settings, window, renderer) } },
        onExit = { window.close() },
        onOpenUrl = { url -> SDL.openURL(url) },
        onPickFolder = {
            folderPickPending = true
        },
        onDriveChanged = { },
        onUnitChanged = { },
        onProfileChanged = {
            settings.mixEnabled = settings.mixMode
            state.resetAll()
            settings.save()
            // The window is user-resizable; the reference layout width
            // (480/680) is handled by sizeX, which anchors the widgets.
        },
        onBenchmarkChanged = { settings.save() },
        onThemeChanged = {
            applyTheme(settings.themeType)
            settings.save()
        },
        onZoomChanged = {
            settings.save()
            // Keep the minimum size in sync with the zoomed reference
            // layout (the user may have enlarged the window).
            val menuH = ImGui.getFrameHeight().toInt()
            window.minimumSize = cn.enaium.sdl.SDLPoint(
                ((if (settings.mixMode) 680 else 480) * zr).toInt(),
                (272 * zr).toInt() + menuH,
            )
        },
        onLangChanged = { settings.save(); store.set("Setting", "Language", Lang.current); store.save() },
        onTestDataChanged = { settings.save() },
        onPresetChanged = { settings.save(); state.resetAll() },
    )

    // ---- frame loop ----
    var running = true
    var frame = 0
    var lastStatus = ""
    var windowSized = false
    while (running && frame < maxFrames) {
        // events
        while (true) {
            val event = SDL.pollEvent() ?: break
            when (event) {
                is SDLEvent.Quit -> running = false
                is SDLEvent.Window ->
                    if (event.type == SDLWindowEventType.CLOSE_REQUESTED) running = false
                is SDLEvent.Key -> {
                    // Forward to imgui (typing, menu navigation) and handle
                    // app shortcuts separately.
                    platform.processEvent(event)
                    val ctrl = event.down && (event.modifiers and SDLKeymod.CTRL) != 0
                    val shift = (event.modifiers and SDLKeymod.SHIFT) != 0
                    if (event.down) {
                        when (event.keycode) {
                            SDLKeycode.C -> if (ctrl && shift) copyText(settings, state, io)
                            SDLKeycode.T -> if (ctrl) runBlocking { saveText(settings, state, io) }
                            SDLKeycode.S -> if (ctrl) runBlocking { saveImage(settings, window, renderer) }
                            SDLKeycode.Q -> if (ctrl) settingsDialog.open()
                            SDLKeycode.F -> if (ctrl) { }
                            SDLKeycode.F1 -> SDL.openURL("https://crystalmark.info/")
                            else -> {}
                        }
                    }
                }
                is SDLEvent.Drop -> {
                    // Drag & drop a folder onto the window.
                    platform.processEvent(event)
                    val path = event.file
                    if (path.isNotEmpty()) {
                        settings.targetPath = path
                        settings.testDriveLetter = 99
                        settings.save()
                        settings.refreshDrives()
                    }
                }
                else -> platform.processEvent(event)
            }
        }

        // folder pick (async: run the suspend picker off the UI frame)
        if (folderPickPending) {
            folderPickPending = false
            val chosen = runBlocking { pickDirectory(settings.targetPath.takeIf { it.isNotEmpty() }) }
            if (chosen != null) {
                settings.targetPath = chosen
                settings.testDriveLetter = 99
                settings.save()
                settings.refreshDrives()
            }
        }

        // engine finished?
        if (benchThread != null && !engine.running) {
            benchThread?.join()
            benchThread = null
            statusMessage = ""
        }

        // ---- imgui frame ----
        platform.newFrame()

        // First frame: the font is now active, so the menu bar height
        // (frame height) is accurate. Correct only the initial window
        // height guess (272*z + 24) to the real content height. The
        // corrected size becomes the minimum: the window can be enlarged
        // but never shrunk below the reference layout (zoom-sized).
        if (!windowSized) {
            val menuH = ImGui.getFrameHeight().toInt()
            val minW = window.size.x
            val minH = (272 * zr).toInt() + menuH
            window.minimumSize = cn.enaium.sdl.SDLPoint(minW, minH)
            window.size = cn.enaium.sdl.SDLPoint(minW, minH)
            windowSized = true
            continue
        }

        // Automated click test: inject a mouse move + click at the "All"
        // button on frame 10, then verify onAll fired by frame 60.
        // Point the target at the home dir so the benchmark actually runs
        // (the default '/' is not writable without root).
        if (clickTest && frame == 9) {
            // Small, fast run so the verdict frame arrives while the
            // benchmark is still active (disabled buttons visible) and
            // finishes before the frame limit.
            settings.testSizeIndex = 0 // 16 MiB
            settings.testCountIndex = 0 // 1 pass
            settings.measureTime = 1 // 1 second per slot
            settings.intervalTime = 0 // no interval pauses
        }
        if (clickTest && frame == 10) {
            val cx = clickTestX
            val cy = clickTestY
            platform.processEvent(
                cn.enaium.sdl.SDLEvent.MouseMotion(0uL, window.id, cx.toFloat(), cy.toFloat(), 0f, 0f)
            )
            platform.processEvent(
                cn.enaium.sdl.SDLEvent.MouseButton(0uL, window.id, true, 1, 1, cx.toFloat(), cy.toFloat())
            )
            platform.processEvent(
                cn.enaium.sdl.SDLEvent.MouseButton(0uL, window.id, false, 1, 1, cx.toFloat(), cy.toFloat())
            )
        }
        if (clickTest && frame == 1300) {
            println("CLICK_TEST result=" + if (clickTestResult) "PASS" else "FAIL")
            running = false
        }

        if (statusMessage != lastStatus) {
            val title = if (statusMessage.isEmpty()) "CrystalDiskMark 9.0.3" else statusMessage
            window.title = title
            lastStatus = statusMessage
        }
        // Irregular-screen safe insets (Android punch holes / rounded
        // corners), mapped from screen pixels into layout units.
        mainUi.safeInsets = io.windowSafeInsets()?.let { safe ->
            val sx = window.size.x.toFloat() / max(window.sizeInPixels.x, 1)
            val sy = window.size.y.toFloat() / max(window.sizeInPixels.y, 1)
            floatArrayOf(safe[0] * sx, safe[1] * sx, safe[2] * sy, safe[3] * sy)
        }
        mainUi.draw()
        settingsDialog.draw()
        aboutDialog.draw()
        ImGui.render()

        renderer.drawColor = SDLColor(18, 18, 18, 255)
        renderer.clear()
        backend.renderDrawData(ImGui.getDrawData())
        renderer.present()
        frame++

        // Screenshot support for UI verification: CDM_SCREENSHOT=<path>.
        // Under --click-test the shot is taken at frame 20 while the
        // benchmark is still active (disabled buttons visible) and the
        // loop continues until the verdict at frame 1300.
        val shotPath = screenshotPath
        if (shotPath != null && if (clickTest) frame == 20 else frame == 5) {
            try {
                val surface = renderer.renderReadPixels(null)
                if (surface != null) {
                    savePng(surface, shotPath)
                    surface.close()
                    io.log("screenshot saved to $shotPath")
                }
            } catch (e: Exception) {
                io.log("screenshot failed: $e")
            }
            if (!clickTest) running = false
        }

        SDL.delay(16)
    }

    stopBenchmark(engine)
    backend.close()
    ImGui.destroyContext(context)
    renderer.close()
    window.close()
    SDL.quit()
}

private var statusMessage: String = ""
private var scoreDirty: Boolean = false
private var benchThread: ThreadHandle? = null
private var screenshotPath: String? = null
private var clickTest = false
private var clickTestX = 44.0
private var clickTestY = 70.0
private var clickTestClicked = false
private var clickTestResult = false

/**
 * Applies one of the imgui preset themes and switches the custom
 * draw-list colors (ThemeColors) to match, so manually drawn text stays
 * readable on the widget backgrounds of the selected preset.
 */
private fun applyTheme(themeType: Int) {
    when (themeType) {
        1 -> {
            ImGui.styleColorsLight()
            cn.enaium.crystaldiskmark.ui.ThemeColors.setLight()
        }
        2 -> {
            ImGui.styleColorsClassic()
            cn.enaium.crystaldiskmark.ui.ThemeColors.setClassic()
        }
        else -> {
            ImGui.styleColorsDark()
            cn.enaium.crystaldiskmark.ui.ThemeColors.setDark()
        }
    }
}

fun setScreenshotPath(path: String) {
    screenshotPath = path
}

fun enableClickTest(x: Double = 44.0, y: Double = 70.0) {
    clickTest = true
    clickTestX = x
    clickTestY = y
}

private fun startBenchmark(engine: BenchmarkEngine, settings: Settings) {
    engine.stop()
    // Rebuild config from current settings.
    val config = settings.engineConfig()
    engine.state = config
    engine.running = true
    engine.singleSlot = -1
    val t = platformIo().spawnThread("benchmark") {
        engine.run()
    }
    engine.thread = t
    benchThread = t
}

private fun startSlotBenchmark(engine: BenchmarkEngine, settings: Settings, index: Int) {
    // Single-slot benchmark: run one slot (read+write) instead of the whole profile.
    val profile = settings.profile
    val slot = when (profile) {
        Profile.DEFAULT.value, Profile.DEFAULT_MIX.value -> index
        Profile.PEAK.value, Profile.PEAK_MIX.value -> if (index == 0) 4 else 5
        Profile.REAL.value, Profile.REAL_MIX.value -> if (index == 0) 6 else 7
        else -> 8
    }
    val config = settings.engineConfig()
    engine.stop()
    engine.state = config
    engine.running = true
    engine.singleSlot = slot
    val t = platformIo().spawnThread("benchmark") {
        engine.run()
    }
    engine.thread = t
    benchThread = t
}

private fun stopBenchmark(engine: BenchmarkEngine) {
    engine.stop()
    benchThread?.join()
    benchThread = null
    engine.thread = null
}

// ----------------------------------------------------------------------
// Text save / copy (mirrors CDiskMarkDlg::SaveText)
// ----------------------------------------------------------------------
private fun buildResultText(settings: Settings, state: AppState): String {
    val sb = StringBuilder()
    sb.append("------------------------------------------------------------------------------\n")
    sb.append("CrystalDiskMark 9.0.3 (C) 2007-2026 hiyohiyo\n")
    sb.append("                                  Crystal Dew World: https://crystalmark.info/\n")
    sb.append("------------------------------------------------------------------------------\n")
    sb.append("* MB/s = 1,000,000 bytes/s [SATA/600 = 600,000,000 bytes/s]\n")
    sb.append("* KB = 1000 bytes, KiB = 1024 bytes\n\n")

    val profile = settings.profile
    val unit = settings.testUnitIndex

    fun slotResult(kind: String, slot: Int): String {
        val s = settings.slots[slotToSettingsRow(slot).coerceAtLeast(0)]
        val score = when (kind) {
            "R" -> state.readScore[slot].score
            "W" -> state.writeScore[slot].score
            else -> state.mixScore[slot].score
        }
        val latency = when (kind) {
            "R" -> state.readScore[slot].latency
            "W" -> state.writeScore[slot].latency
            else -> state.mixScore[slot].latency
        }
        val type = if (s.type == BenchType.RND.value) "RND" else "SEQ"
        val sizeStr = if (s.size >= 1024) "${s.size / 1024}MiB" else "${s.size}KiB"
        val iops = score * 1000 * 1000 / (s.size * 1024)
        return Fmt.format(
            "  %s %4s (Q=%3d, T=%2d): %9.3f MB/s [%9.1f IOPS] <%9.2f us>",
            type, sizeStr, s.queues, s.threads, score, iops, latency
        )
    }

    sb.append("[Read]\n")
    when (profile) {
        Profile.DEMO.value -> sb.append(slotResult("R", 8)).append("\n\n")
        Profile.PEAK.value, Profile.PEAK_MIX.value -> {
            sb.append(slotResult("R", 4)).append("\n")
            sb.append(slotResult("R", 5)).append("\n\n")
        }
        Profile.REAL.value, Profile.REAL_MIX.value -> {
            sb.append(slotResult("R", 6)).append("\n")
            sb.append(slotResult("R", 7)).append("\n\n")
        }
        else -> {
            for (i in 0 until 4) sb.append(slotResult("R", i)).append("\n")
            sb.append("\n")
        }
    }

    sb.append("[Write]\n")
    when (profile) {
        Profile.DEMO.value -> sb.append(slotResult("W", 8)).append("\n\n")
        Profile.PEAK.value, Profile.PEAK_MIX.value -> {
            sb.append(slotResult("W", 4)).append("\n")
            sb.append(slotResult("W", 5)).append("\n\n")
        }
        Profile.REAL.value, Profile.REAL_MIX.value -> {
            sb.append(slotResult("W", 6)).append("\n")
            sb.append(slotResult("W", 7)).append("\n\n")
        }
        else -> {
            for (i in 0 until 4) sb.append(slotResult("W", i)).append("\n")
            sb.append("\n")
        }
    }

    if (settings.mixMode) {
        sb.append("[Mix] Read ").append(100 - settings.mixWriteRatio)
            .append("%/Write ").append(settings.mixWriteRatio).append("%\n")
        when (profile) {
            Profile.PEAK_MIX.value -> {
                sb.append(slotResult("M", 4)).append("\n")
                sb.append(slotResult("M", 5)).append("\n\n")
            }
            Profile.REAL_MIX.value -> {
                sb.append(slotResult("M", 6)).append("\n")
                sb.append(slotResult("M", 7)).append("\n\n")
            }
            else -> {
                for (i in 0 until 4) sb.append(slotResult("M", i)).append("\n")
                sb.append("\n")
            }
        }
    }

    sb.append("Profile: ").append(
        when (profile) {
            Profile.DEMO.value -> "Demo"
            Profile.PEAK.value, Profile.PEAK_MIX.value -> "Peak"
            Profile.REAL.value, Profile.REAL_MIX.value -> "Real"
            else -> "Default"
        }
    ).append("\n")
    val sizeLabel = Settings.TEST_SIZES[settings.testSizeIndex]
    sb.append("   Test: ").append(sizeLabel).append(" (x").append(settings.testCount()).append(")")
    if (settings.selectedDrivePath().isNotEmpty()) {
        sb.append(" [").append(settings.selectedDrivePath()).append("]")
    }
    sb.append("\n")
    sb.append("   Mode:").append(if (settings.testData == TestData.ALL_0X00.value) " <0Fill>" else "").append("\n")
    sb.append("   Time: Measure ").append(settings.measureTime).append(" / Interval ").append(settings.intervalTime).append(" \n")
    val now = platformIo().currentTimeMillis()
    sb.append("   Date: ").append(dateString(now)).append("\n")
    sb.append("     OS: ").append(platformIo().osName()).append("\n")
    if (settings.comment.isNotEmpty()) {
        sb.append("Comment: ").append(settings.comment).append("\n")
    }
    return sb.toString()
}

private fun copyText(settings: Settings, state: AppState, io: PlatformIo) {
    SDL.setClipboardText(buildResultText(settings, state))
}

private suspend fun saveText(settings: Settings, state: AppState, io: PlatformIo) {
    val path = saveFileDialog("CrystalDiskMark_${timestamp()}", "txt", setOf("txt")) ?: return
    io.writeTextFile(path, buildResultText(settings, state))
}

// ----------------------------------------------------------------------
// Image save
// ----------------------------------------------------------------------
private fun saveImage(settings: Settings, window: SDLWindow, renderer: SDLRenderer) {
    val path = runBlocking {
        saveFileDialog(
            "CrystalDiskMark_${timestamp()}", "png", setOf("png", "bmp")
        )
    } ?: return
    try {
        val surface = renderer.renderReadPixels(null) ?: return
        try {
            val ok = if (path.endsWith(".bmp", ignoreCase = true)) {
                surface.saveBMP(path)
            } else {
                savePng(surface, path)
            }
            if (ok) platformIo().log("saved $path")
        } finally {
            surface.close()
        }
    } catch (e: Exception) {
        platformIo().log("save image failed: $e")
    }
}

private fun timestamp(): String {
    return dateString(platformIo().currentTimeMillis()).replace("/", "").replace(":", "").replace(" ", "")
}

/** yyyy/MM/dd H:mm:ss from epoch millis (local time). */
private fun dateString(millis: Long): String {
    var seconds = millis / 1000
    // Days since epoch; civil calendar algorithm (Howard Hinnant).
    var z = seconds / 86400 + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    val secOfDay = seconds % 86400
    val h = secOfDay / 3600
    val mi = (secOfDay % 3600) / 60
    val s = secOfDay % 60
    return Fmt.format("%04d/%02d/%02d %d:%02d:%02d", year, m, d, h, mi, s)
}

/** Minimal PNG writer (RGBA, no compression) for screenshot export. */
private fun savePng(surface: SDLSurface, path: String): Boolean {
    val w = surface.width
    val h = surface.height
    val pitch = surface.pitch
    val pixels = surface.pixels
    if (w <= 0 || h <= 0) return false

    // 32-bit RGBA scanlines, top-down (SDL surface memory starts at the
    // first row), no filter.
    val raw = ByteArray(h * (w * 4 + 1))
    var rawPos = 0
    for (y in 0 until h) {
        raw[rawPos++] = 0 // filter: none
        var rowStart = y * pitch
        for (x in 0 until w) {
            val b = pixels[rowStart + x * 4 + 0].toInt() and 0xFF
            val g = pixels[rowStart + x * 4 + 1].toInt() and 0xFF
            val r = pixels[rowStart + x * 4 + 2].toInt() and 0xFF
            val a = pixels[rowStart + x * 4 + 3].toInt() and 0xFF
            raw[rawPos++] = r.toByte()
            raw[rawPos++] = g.toByte()
            raw[rawPos++] = b.toByte()
            raw[rawPos++] = a.toByte()
        }
    }

    // PNG chunks with a stored (uncompressed) zlib stream.
    val out = ArrayList<ByteArray>()
    fun chunk(type: String, data: ByteArray) {
        val len = ByteArray(4)
        len[0] = (data.size ushr 24).toByte(); len[1] = (data.size ushr 16).toByte()
        len[2] = (data.size ushr 8).toByte(); len[3] = data.size.toByte()
        val crc = crc32(type.encodeToByteArray() + data)
        val crcB = ByteArray(4)
        crcB[0] = (crc ushr 24).toByte(); crcB[1] = (crc ushr 16).toByte()
        crcB[2] = (crc ushr 8).toByte(); crcB[3] = crc.toByte()
        out.add(len); out.add(type.encodeToByteArray()); out.add(data); out.add(crcB)
    }

    // Header
    val header = ByteArray(8)
    header[0] = 0x89.toByte(); header[1] = 0x50.toByte(); header[2] = 0x4E.toByte(); header[3] = 0x47.toByte()
    header[4] = 0x0D.toByte(); header[5] = 0x0A.toByte(); header[6] = 0x1A.toByte(); header[7] = 0x0A.toByte()
    out.add(header)

    // IHDR: width, height, bit depth 8, color type 6 (RGBA)
    val ihdr = ByteArray(13)
    ihdr[0] = (w ushr 24).toByte(); ihdr[1] = (w ushr 16).toByte(); ihdr[2] = (w ushr 8).toByte(); ihdr[3] = w.toByte()
    ihdr[4] = (h ushr 24).toByte(); ihdr[5] = (h ushr 16).toByte(); ihdr[6] = (h ushr 8).toByte(); ihdr[7] = h.toByte()
    ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0
    chunk("IHDR", ihdr)

    // IDAT: zlib stream, stored blocks.
    val idat = zlibStored(raw)
    chunk("IDAT", idat)

    // IEND
    chunk("IEND", ByteArray(0))

    val bytes = ByteArray(out.sumOf { it.size })
    var pos = 0
    for (b in out) {
        b.copyInto(bytes, pos)
        pos += b.size
    }
    platformIo().writeTextFile(path, "")
    // writeTextFile writes text; use raw file instead:
    return writeBytesFile(path, bytes)
}

private fun writeBytesFile(path: String, bytes: ByteArray): Boolean {
    return try {
        val io = platformIo()
        val f = io.openFile(path, true)
        try {
            f.writeAt(0, bytes, 0, bytes.size)
            f.setLength(bytes.size.toLong())
        } finally {
            f.close()
        }
        true
    } catch (e: Exception) {
        false
    }
}

/** zlib wrapper with stored (uncompressed) deflate blocks. */
private fun zlibStored(data: ByteArray): ByteArray {
    val out = ArrayList<ByteArray>()
    // zlib header: CMF=0x78, FLG=0x01 (no dict, FCHECK ok)
    out.add(byteArrayOf(0x78.toByte(), 0x01.toByte()))

    // adler32 of raw data
    val adler = adler32(data)
    val adlerB = ByteArray(4)
    adlerB[0] = (adler ushr 24).toByte(); adlerB[1] = (adler ushr 16).toByte()
    adlerB[2] = (adler ushr 8).toByte(); adlerB[3] = adler.toByte()

    var pos = 0
    val chunkSize = 0xFFFF
    while (pos < data.size) {
        val len = minOf(chunkSize, data.size - pos)
        val block = ByteArray(5 + len)
        // BFINAL=1 on last block, BTYPE=00 (stored)
        block[0] = if (pos + len >= data.size) 0x01 else 0x00
        block[1] = len.toByte()
        block[2] = (len shr 8).toByte()
        block[3] = (len xor 0xFFFF).toByte()
        block[4] = ((len xor 0xFFFF) shr 8).toByte()
        data.copyInto(block, 5, pos, pos + len)
        out.add(block)
        pos += len
    }
    if (data.isEmpty()) {
        out.add(byteArrayOf(0x01, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte()))
    }
    out.add(adlerB)

    val bytes = ByteArray(out.sumOf { it.size })
    var p = 0
    for (b in out) {
        b.copyInto(bytes, p)
        p += b.size
    }
    return bytes
}

private fun adler32(data: ByteArray): Int {
    var a = 1
    var b = 0
    for (byte in data) {
        a = (a + (byte.toInt() and 0xFF)) % 65521
        b = (b + a) % 65521
    }
    return (b shl 16) or a
}

private fun crc32(data: ByteArray): Int {
    var crc = 0xFFFFFFFFL
    for (byte in data) {
        crc = crc xor (byte.toLong() and 0xFF)
        for (i in 0 until 8) {
            crc = if ((crc and 1L) != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1
        }
    }
    return (crc xor 0xFFFFFFFFL).toInt()
}

// ----------------------------------------------------------------------
// System font lookup (CJK-capable)
// ----------------------------------------------------------------------
private fun findSystemFont(): String? {
    val candidates = when {
        Platform.os == "macos" || Platform.os == "ios" || Platform.os == "tvos" ->
            listOf(
                "/System/Library/Fonts/PingFang.ttc",
                "/System/Library/Fonts/STHeiti Light.ttc",
                "/System/Library/Fonts/Hiragino Sans GB.ttc",
                "/System/Library/Fonts/Supplemental/Songti.ttc",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            )
        Platform.os == "windows" ->
            listOf(
                "C:\\Windows\\Fonts\\msyh.ttc",
                "C:\\Windows\\Fonts\\msyh.ttf",
                "C:\\Windows\\Fonts\\simhei.ttf",
                "C:\\Windows\\Fonts\\simsun.ttc",
            )
        Platform.os == "linux" ->
            listOf(
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf",
            )
        else -> emptyList()
    }
    return candidates.firstOrNull { platformIo().fileExists(it) }
}

private object Platform {
    val os: String
        get() {
            val name = platformIo().osName().lowercase()
            return when {
                name.contains("mac") || name.contains("darwin") -> "macos"
                name.contains("win") -> "windows"
                name.contains("linux") -> "linux"
                else -> "other"
            }
        }
}
