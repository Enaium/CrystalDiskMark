@file:JvmName("CrystalDiskMark")

package cn.enaium.crystaldiskmark

import io.github.vinceglb.filekit.FileKit

/**
 * JVM entry point.
 *
 * Flags:
 *  --frames N      exit after N rendered frames (headless CI smoke test)
 *  --smoke         run the benchmark-engine smoke test and exit
 *  --width W       window width
 *  --height H      window height
 */
fun main(args: Array<String>) {
    var width = 480
    var height = 300
    var frames = Int.MAX_VALUE
    var smoke = false
    var screenshot: String? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--frames" -> if (i + 1 < args.size) { frames = args[++i].toIntOrNull() ?: Int.MAX_VALUE }
            "--width" -> if (i + 1 < args.size) { width = args[++i].toIntOrNull() ?: width }
            "--height" -> if (i + 1 < args.size) { height = args[++i].toIntOrNull() ?: height }
            "--smoke" -> smoke = true
            "--screenshot" -> if (i + 1 < args.size) { screenshot = args[++i] }
            "--click-test" -> {
                val x = args.getOrNull(i + 1)?.toDoubleOrNull()
                val y = args.getOrNull(i + 2)?.toDoubleOrNull()
                if (x != null && y != null) {
                    enableClickTest(x, y)
                    i += 2
                } else {
                    enableClickTest()
                }
            }
            "--help", "-h" -> {
                println("CrystalDiskMark [--frames N] [--width W] [--height H] [--smoke] [--screenshot PATH]")
                return
            }
        }
        i++
    }
    if (smoke) {
        runEngineSmokeTest()
        return
    }
    if (screenshot != null) {
        setScreenshotPath(screenshot!!)
    }
    // FileKit JVM needs an app id for its base dirs.
    FileKit.init("CrystalDiskMark")
    runCrystalDiskMark(width = width, height = height, maxFrames = frames)
}
