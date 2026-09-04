package cn.enaium.crystaldiskmark

import cn.enaium.crystaldiskmark.engine.BenchmarkEngine
import cn.enaium.crystaldiskmark.platform.platformIo

/**
 * Headless smoke test for the benchmark engine (native).
 * Creates a 64 MiB test file in the config dir and runs a single
 * sequential-read + sequential-write slot, printing the scores.
 */
fun runEngineSmokeTest() {
    val io = platformIo()
    val dir = io.configDir() + "/smoke"
    io.createDirectory(dir)
    val listener = object : BenchmarkEngine.Listener {
        override fun onStatus(message: String) {
            println("[status] $message")
        }

        override fun onSlotScore(kind: BenchmarkEngine.ScoreKind, slot: Int, score: Double, latency: Double) {
            println("[score] $kind slot=$slot score=${fmt3(score)} MB/s latency=${fmt2(latency)} us")
        }
    }
    val config = BenchmarkEngine.EngineConfig(
        testSizeMiB = 64,
        testCount = 2,
        testData = TestData.RANDOM.value,
        profile = Profile.DEFAULT.value,
        benchmark = BenchmarkMode.READ_WRITE.value,
        measureTime = 1,
        intervalTime = 0,
        targetPath = dir,
        slots = (0 until 9).map {
            when (it) {
                0 -> BenchSetting(0, 1024, 8, 1)  // SEQ 1MiB Q8T1
                1 -> BenchSetting(0, 1024, 1, 1)  // SEQ 1MiB Q1T1
                2 -> BenchSetting(1, 4, 32, 1)    // RND 4KiB Q32T1
                3 -> BenchSetting(1, 4, 1, 1)     // RND 4KiB Q1T1
                else -> BenchSetting()
            }
        },
    )
    val engine = BenchmarkEngine(io, config, listener)
    engine.singleSlot = 0
    engine.run()
    println("SMOKE_OK")
}

private fun fmt3(v: Double): String {
    val s = (v * 1000).toLong().toString().padStart(4, '0')
    return s.dropLast(3) + "." + s.takeLast(3)
}

private fun fmt2(v: Double): String {
    val s = (v * 100).toLong().toString().padStart(3, '0')
    return s.dropLast(2) + "." + s.takeLast(2)
}
