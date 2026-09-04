@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

import cn.enaium.crystaldiskmark.runCrystalDiskMark
import cn.enaium.crystaldiskmark.runEngineSmokeTest
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Kotlin/Native entry point (K/N requires the executable entry in the
 * default package). Reads environment variables for headless smoke tests.
 *
 *  IMGUI_KMP_FRAMES  exit after N rendered frames
 *  CDM_WIDTH         window width
 *  CDM_HEIGHT        window height
 *  CDM_SMOKE         run the benchmark-engine smoke test and exit
 */
fun main() {
    val smoke = getenv("CDM_SMOKE")?.toKString()
    if (smoke != null && smoke.isNotEmpty()) {
        runEngineSmokeTest()
        return
    }
    val clickTest = getenv("CDM_CLICK_TEST")?.toKString()
    if (clickTest != null && clickTest.isNotEmpty()) {
        cn.enaium.crystaldiskmark.enableClickTest()
    }
    getenv("CDM_SCREENSHOT")?.toKString()?.takeIf { it.isNotEmpty() }?.let { cn.enaium.crystaldiskmark.setScreenshotPath(it) }
    val frames = getenv("IMGUI_KMP_FRAMES")?.toKString()?.toIntOrNull() ?: Int.MAX_VALUE
    val width = getenv("CDM_WIDTH")?.toKString()?.toIntOrNull() ?: 480
    val height = getenv("CDM_HEIGHT")?.toKString()?.toIntOrNull() ?: 300
    runCrystalDiskMark(width = width, height = height, maxFrames = frames)
}
