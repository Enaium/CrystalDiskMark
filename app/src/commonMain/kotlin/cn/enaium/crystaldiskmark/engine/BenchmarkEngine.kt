package cn.enaium.crystaldiskmark.engine

import cn.enaium.crystaldiskmark.*
import cn.enaium.crystaldiskmark.platform.PlatformIo
import cn.enaium.crystaldiskmark.platform.ThreadHandle
import cn.enaium.crystaldiskmark.platform.atomicCounter
import cn.enaium.crystaldiskmark.platform.RandomAccessFile
import kotlin.random.Random
import kotlin.math.min

/**
 * Pure-Kotlin benchmark engine equivalent to CrystalDiskMark's
 * DiskBench.cpp (which shells out to diskspd.exe).
 *
 * Terminology (matching the reference):
 *  - "measure time": per-run duration in seconds
 *  - "test count": number of scored runs per slot (plus one warmup run)
 *  - "queues": outstanding I/Os per thread (queue depth)
 *  - "threads": parallel I/O threads per slot
 *  - score: MB/s = 1,000,000 bytes/s (decimal, same as diskspd)
 *  - latency: average I/O latency in microseconds
 *
 * Flow per slot (mirrors DiskSpd()):
 *   1. warmup run (j == 0, status "Preparing... <name>")
 *   2. runs 1..testCount (status "<name> (j/count)")
 *   3. max(score) and min(latency) across scored runs are reported.
 */
class BenchmarkEngine(
    private val io: PlatformIo,
    state: EngineConfig,
    private val listener: Listener,
) {
    /** Current config (rebuildable between runs). */
    var state: EngineConfig = state

    /** Handle of the running benchmark thread (managed by Main). */
    var thread: cn.enaium.crystaldiskmark.platform.ThreadHandle? = null

    /** When >= 0, only this slot is benchmarked (single-test buttons). */
    var singleSlot: Int = -1
    interface Listener {
        fun onStatus(message: String)
        fun onSlotScore(kind: ScoreKind, slot: Int, score: Double, latency: Double)
    }

    enum class ScoreKind { READ, WRITE, MIX }

    data class EngineConfig(
        var testSizeMiB: Int = 1024,
        var testCount: Int = 3,          // scored runs (combo index + 1)
        var testData: Int = TestData.RANDOM.value,
        var profile: Int = Profile.DEFAULT.value,
        var benchmark: Int = BenchmarkMode.READ_WRITE.value,
        var mixEnabled: Boolean = false,
        var mixWriteRatio: Int = 30,
        var measureTime: Int = 5,
        var intervalTime: Int = 5,
        var targetPath: String = "",
        var slots: List<BenchSetting> = DEFAULT_SETTINGS.map { it.copy() },
    )

    var running: Boolean = true

    private var testDir: String = ""
    private var testFile: String = ""

    /** Shared per-slot stats; written by workers, read by the timer. */
    private class SlotStats {
        val bytes = atomicCounter(0)
        val ops = atomicCounter(0)
        val latencyUs = atomicCounter(0)
        fun reset() {
            bytes.addAndGet(-bytes.get())
            ops.addAndGet(-ops.get())
            latencyUs.addAndGet(-latencyUs.get())
        }
    }

    private fun slotName(kind: ScoreKind, slot: Int): String {
        val s = state.slots[slot]
        val type = if (s.type == BenchType.SEQ.value) "Sequential" else "Random"
        return when (kind) {
            ScoreKind.READ -> "$type Read"
            ScoreKind.WRITE -> "$type Write"
            ScoreKind.MIX -> "$type Mix"
        }
    }

    fun run() {
        try {
            if (!initFile()) {
                listener.onStatus("")
                return
            }
            if (singleSlot >= 0) {
                runSingle(singleSlot)
            } else {
                runAll()
            }
        } finally {
            // The benchmark finished (or was stopped): clear the running
            // flag so the UI can reap the thread and re-enable the buttons.
            running = false
            cleanup()
            listener.onStatus("")
        }
    }

    private fun runSingle(slot: Int) {
        val readWanted = state.benchmark and BenchmarkMode.READ_ONLY.value != 0
        val writeWanted = state.benchmark and BenchmarkMode.WRITE_ONLY.value != 0
        if (readWanted) {
            runSlot(ScoreKind.READ, slot)
            if (running && writeWanted) interval()
        }
        if (writeWanted) {
            runSlot(ScoreKind.WRITE, slot)
        }
        if (state.mixEnabled && running) {
            if (readWanted || writeWanted) interval()
            runSlot(ScoreKind.MIX, slot)
        }
    }

    /** Stop flag honored between runs and within fill loops. */
    fun stop() {
        running = false
    }

    private fun initFile(): Boolean {
        val root = state.targetPath
        if (root.isEmpty()) return false

        // Disk capacity check (reference: DiskTestSize(MiB) <= free MiB).
        val freeMiB = io.freeSpace(root) / (1024 * 1024)
        if (state.testSizeMiB > freeMiB) {
            listener.onStatus("DISK_CAPACITY_ERROR")
            return false
        }

        listener.onStatus("Preparing... Create Test File")
        val stamp = (io.currentTimeMillis() and 0x7FFFFFFF)
        val sep = sep()
        testDir = root.trimEnd('/') + "/CrystalDiskMark" + hex8(stamp)
        io.createDirectory(testDir)
        testFile = testDir + "/CrystalDiskMark" + hex8((io.currentTimeMillis() and 0x7FFFFFFF) xor 0x5A5A5A5A) + ".tmp"

        val file = try {
            io.openFile(testFile, true)
        } catch (e: Exception) {
            listener.onStatus("DISK_CREATE_FILE_ERROR")
            return false
        }

        try {
            val sizeBytes = state.testSizeMiB.toLong() * 1024 * 1024
            file.setLength(sizeBytes)

            // Fill test data (reference: 1 MiB buffer written in a loop).
            val bufSize = 1024 * 1024
            val buf = ByteArray(bufSize)
            if (state.testData == TestData.ALL_0X00.value) {
                buf.fill(0)
            } else {
                // Compatible with DiskSpd's rand() % 256 pattern.
                val rng = Random(0x12345678)
                for (i in buf.indices) buf[i] = (rng.nextInt(256)).toByte()
            }
            var offset = 0L
            while (offset < sizeBytes && running) {
                val len = min(bufSize.toLong(), sizeBytes - offset).toInt()
                file.writeAt(offset, buf, 0, len)
                offset += len
            }
            if (!running) return false
        } catch (e: Exception) {
            listener.onStatus("DISK_WRITE_ERROR")
            return false
        } finally {
            file.close()
        }
        return running
    }

    private fun cleanup() {
        if (testFile.isNotEmpty()) {
            try { io.deleteFile(testFile) } catch (_: Exception) {}
        }
        if (testDir.isNotEmpty()) {
            try { io.deleteFile(testDir) } catch (_: Exception) {}
        }
    }

    private fun sep(): String = "/"

    /** 8-digit uppercase hex, mirroring the reference's %08X. */
    private fun hex8(v: Long): String {
        val h = v.toString(16).uppercase().padStart(8, '0')
        return h
    }

    // ------------------------------------------------------------------
    // Benchmark drivers
    // ------------------------------------------------------------------

    private fun runAll() {
        val benchmark = state.benchmark
        val profile = state.profile
        val isMix = state.mixEnabled
        val readWanted = benchmark and BenchmarkMode.READ_ONLY.value != 0
        val writeWanted = benchmark and BenchmarkMode.WRITE_ONLY.value != 0

        when (profile) {
            Profile.DEFAULT.value, Profile.DEFAULT_MIX.value -> {
                if (readWanted) {
                    runSlot(ScoreKind.READ, 0); if (!running) return; interval()
                    runSlot(ScoreKind.READ, 1); if (!running) return; interval()
                    runSlot(ScoreKind.READ, 2); if (!running) return; interval()
                    runSlot(ScoreKind.READ, 3)
                }
                if (readWanted && writeWanted) { if (!running) return; interval() }
                if (writeWanted) {
                    runSlot(ScoreKind.WRITE, 0); if (!running) return; interval()
                    runSlot(ScoreKind.WRITE, 1); if (!running) return; interval()
                    runSlot(ScoreKind.WRITE, 2); if (!running) return; interval()
                    runSlot(ScoreKind.WRITE, 3)
                }
                if (isMix) {
                    if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 0); if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 1); if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 2); if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 3)
                }
            }
            Profile.PEAK.value, Profile.PEAK_MIX.value -> {
                if (readWanted) {
                    runSlot(ScoreKind.READ, 4); if (!running) return; interval()
                    runSlot(ScoreKind.READ, 5)
                }
                if (readWanted && writeWanted) { if (!running) return; interval() }
                if (writeWanted) {
                    runSlot(ScoreKind.WRITE, 4); if (!running) return; interval()
                    runSlot(ScoreKind.WRITE, 5)
                }
                if (isMix) {
                    if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 4); if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 5)
                }
            }
            Profile.REAL.value, Profile.REAL_MIX.value -> {
                if (readWanted) {
                    runSlot(ScoreKind.READ, 6); if (!running) return; interval()
                    runSlot(ScoreKind.READ, 7)
                }
                if (readWanted && writeWanted) { if (!running) return; interval() }
                if (writeWanted) {
                    runSlot(ScoreKind.WRITE, 6); if (!running) return; interval()
                    runSlot(ScoreKind.WRITE, 7)
                }
                if (isMix) {
                    if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 6); if (!running) return; interval()
                    runSlot(ScoreKind.MIX, 7)
                }
            }
            Profile.DEMO.value -> {
                if (readWanted) runSlot(ScoreKind.READ, 8)
                if (readWanted && writeWanted) { if (!running) return; interval() }
                if (writeWanted) runSlot(ScoreKind.WRITE, 8)
            }
        }
    }

    private fun interval() {
        val seconds = state.intervalTime
        for (i in 0 until seconds) {
            if (!running) return
            listener.onStatus("Interval Time $i/$seconds sec")
            sleep(1000)
        }
    }

    private fun sleep(ms: Long) {
        io.sleep(ms)
    }

    // ------------------------------------------------------------------
    // One slot: warmup + scored runs (mirrors DiskSpd())
    // ------------------------------------------------------------------

    private fun runSlot(kind: ScoreKind, slot: Int) {
        if (!running) return
        val setting = state.slots[slot]
        val name = slotName(kind, slot)
        val durationMs = state.measureTime * 1000L
        val count = state.testCount

        var maxScore = 0.0
        var minLatency = -1.0

        for (j in 0..count) {
            if (!running) return
            val status = if (j == 0) "Preparing... $name" else "$name ($j/$count)"
            listener.onStatus(status)

            val stats = SlotStats()
            val result = runOnce(setting, kind, durationMs, stats)

            if (j > 0 && result.score > maxScore) {
                maxScore = result.score
                listener.onSlotScore(kind, slot, maxScore, result.latency)
            }
            if (j > 0 && result.score > 0.0 && (result.latency < minLatency || minLatency < 0.0)) {
                minLatency = result.latency
                listener.onSlotScore(kind, slot, maxScore, minLatency)
            }
        }
        listener.onSlotScore(kind, slot, maxScore, minLatency)
    }

    private class RunResult(val score: Double, val latency: Double)

    private fun runOnce(
        setting: BenchSetting,
        kind: ScoreKind,
        durationMs: Long,
        stats: SlotStats,
    ): RunResult {
        val file = try {
            io.openFile(testFile, false)
        } catch (e: Exception) {
            listener.onStatus("DISK_READ_ERROR")
            return RunResult(0.0, 0.0)
        }
        try {
            val sizeBytes = io.fileSize(testFile).coerceAtLeast(1L)
            val blockBytes = setting.size * 1024L
            if (blockBytes <= 0 || sizeBytes <= 0) return RunResult(0.0, 0.0)

            val threads = setting.threads.coerceIn(1, MAX_THREADS)
            val queues = setting.queues.coerceIn(1, MAX_QUEUES)
            val isRandom = setting.type == BenchType.RND.value

            // Partition the file across threads (sequential: contiguous
            // per-thread regions; random: whole file, independent PRNG).
            val regionBytes = sizeBytes / threads
            val workers = mutableListOf<ThreadHandle>()

            val startMs = io.currentTimeMillis()
            val deadline = startMs + durationMs

            for (t in 0 until threads) {
                for (q in 0 until queues) {
                    val seed = (t * 7919 + q * 104729 + slotSeed(kind)).toLong()
                    val regionStart = t * regionBytes
                    val regionSize = if (t == threads - 1) sizeBytes - regionStart else regionBytes
                    workers.add(spawnWorker(file, setting, kind, stats, regionStart, regionSize, blockBytes, isRandom, seed, deadline))
                }
            }

            // Measure until the deadline.
            var lastSampleMs = startMs
            var lastBytes = 0L
            while (io.currentTimeMillis() < deadline && running) {
                io.sleep(50)
                val now = io.currentTimeMillis()
                // Stall detection: if no progress for 2s, abort this run.
                val b = stats.bytes.get()
                if (now - lastSampleMs >= 2000 && b == lastBytes) {
                    break
                }
                lastSampleMs = now
                lastBytes = b
            }

            val endMs = io.currentTimeMillis()
            val elapsedMs = (endMs - startMs).coerceAtLeast(1)
            val totalBytes = stats.bytes.get()
            val totalOps = stats.ops.get()
            val totalLatency = stats.latencyUs.get()

            val score = totalBytes.toDouble() / elapsedMs / 1000.0  // MB/s (decimal)
            val latency = if (totalOps > 0) totalLatency.toDouble() / totalOps else 0.0

            workers.forEach { it.join() }
            return RunResult(score, latency)
        } finally {
            file.close()
        }
    }

    private fun slotSeed(kind: ScoreKind): Int = when (kind) {
        ScoreKind.READ -> 0x1111
        ScoreKind.WRITE -> 0x2222
        ScoreKind.MIX -> 0x3333
    }

    private fun spawnWorker(
        file: RandomAccessFile,
        setting: BenchSetting,
        kind: ScoreKind,
        stats: SlotStats,
        regionStart: Long,
        regionSize: Long,
        blockBytes: Long,
        isRandom: Boolean,
        seed: Long,
        deadline: Long,
    ): ThreadHandle {
        return io.spawnThread("bench") {
            val buf = ByteArray(blockBytes.toInt())
            var pos = regionStart
            val rng = Random(seed)
            val blockCount = (regionSize / blockBytes).coerceAtLeast(1)
            // Random offsets must be block-aligned within [regionStart, regionStart+regionSize).
            val randBlockLimit = blockCount

            while (running && io.currentTimeMillis() < deadline) {
                val offset: Long
                if (isRandom) {
                    val block = (rng.nextLong() and Long.MAX_VALUE) % randBlockLimit
                    offset = regionStart + block * blockBytes
                } else {
                    offset = pos
                    pos += blockBytes
                    if (pos >= regionStart + regionSize) pos = regionStart
                }

                val opStart = io.nanoTime()
                try {
                    when (kind) {
                        ScoreKind.READ -> {
                            var done = 0
                            while (done < blockBytes && running) {
                                val n = file.readAt(offset + done, buf, done, (blockBytes - done).toInt())
                                if (n <= 0) break
                                done += n
                            }
                        }
                        ScoreKind.WRITE -> file.writeAt(offset, buf, 0, blockBytes.toInt())
                        ScoreKind.MIX -> {
                            val doWrite = (rng.nextInt(100) < state.mixWriteRatio)
                            if (doWrite) file.writeAt(offset, buf, 0, blockBytes.toInt())
                            else {
                                var done = 0
                                while (done < blockBytes && running) {
                                    val n = file.readAt(offset + done, buf, done, (blockBytes - done).toInt())
                                    if (n <= 0) break
                                    done += n
                                }
                            }
                        }
                    }
                    val latencyUs = (io.nanoTime() - opStart) / 1000
                    stats.latencyUs.addAndGet(latencyUs)
                    stats.ops.incrementAndGet()
                    stats.bytes.addAndGet(blockBytes)
                } catch (_: Exception) {
                    // I/O error: stop this worker.
                    return@spawnThread
                }
            }
        }
    }
}
