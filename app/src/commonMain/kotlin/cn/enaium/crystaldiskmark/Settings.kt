package cn.enaium.crystaldiskmark

import cn.enaium.crystaldiskmark.engine.BenchmarkEngine

/**
 * App settings; persisted to the INI store. Mirrors the reference's
 * [Setting] section keys.
 */
class Settings(private val store: ConfigStore) {

    companion object {
        val TEST_SIZES = arrayOf("16MiB", "32MiB", "64MiB", "128MiB", "256MiB", "512MiB",
            "1GiB", "2GiB", "4GiB", "8GiB", "16GiB", "32GiB", "64GiB")

        fun testSizeMiB(index: Int): Int {
            val label = TEST_SIZES[index.coerceIn(0, TEST_SIZES.size - 1)]
            val value = label.removeSuffix("MiB").removeSuffix("GiB").toIntOrNull() ?: 1
            return if (label.endsWith("GiB")) value * 1024 else value
        }

        val MEASURE_TIMES = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 60)
        val INTERVAL_TIMES = intArrayOf(0, 1, 3, 5, 10, 30, 60, 180, 300, 600)
        val QUEUE_VALUES = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512)
        val BLOCK_SIZES = intArrayOf(4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192)
    }

    // ---- persisted state ----
    var testCountIndex: Int = 2
    var testSizeIndex: Int = 6
    var testUnitIndex: Int = 0
    var testDriveIndex: Int = 0
    var testDriveLetter: Int = 2   // default C:
    var targetPath: String = ""
    var testData: Int = TestData.RANDOM.value
    var profile: Int = Profile.DEFAULT.value
    var benchmark: Int = BenchmarkMode.READ_WRITE.value
    var mixEnabled: Boolean = false
    var mixWriteRatio: Int = 30
    var measureTime: Int = 5
    var intervalTime: Int = 5
    var zoomType: Int = 200        // default 200% (0 = auto)
    /** imgui preset theme: 0 = Dark (default), 1 = Light, 2 = Classic. */
    var themeType: Int = 0
    var fontFace: String = ""
    var fontScale: Int = 100
    var comment: String = ""

    var slots: List<BenchSetting> = DEFAULT_SETTINGS.map { it.copy() }

    // ---- runtime state ----
    var drives: List<DriveInfo> = emptyList()
    var driveItems: List<String> = emptyList()

    val mixMode: Boolean
        get() = Profile.entries.firstOrNull { it.value == profile }?.isMix == true

    val zoomRatio: Float
        get() = when (zoomType) {
            100 -> 1.0f
            125 -> 1.25f
            150 -> 1.5f
            200 -> 2.0f
            250 -> 2.5f
            300 -> 3.0f
            else -> 1.0f
        }

    fun testSizeMiB(): Int = testSizeMiB(testSizeIndex)

    fun testCount(): Int = testCountIndex + 1

    // ------------------------------------------------------------------
    // Load / save
    // ------------------------------------------------------------------
    fun load() {
        store.load()
        testCountIndex = store.getInt("Setting", "TestCount", 2).coerceIn(0, 8)
        testSizeIndex = store.getInt("Setting", "TestSize", 6).coerceIn(0, TEST_SIZES.size - 1)
        testUnitIndex = store.getInt("Setting", "TestUnit", 0).coerceIn(0, 3)
        testDriveLetter = store.getInt("Setting", "DriveLetter", 2)
        targetPath = store.get("Setting", "TargetPath", "")
        testData = if (store.getInt("Setting", "TestData", 0) == TestData.ALL_0X00.value) {
            TestData.ALL_0X00.value
        } else {
            TestData.RANDOM.value
        }
        profile = store.getInt("Setting", "Profile", Profile.DEFAULT.value).coerceIn(0, Profile.REAL_MIX.value)
        benchmark = store.getInt("Setting", "Benchmark", BenchmarkMode.READ_WRITE.value).coerceIn(1, 3)
        mixWriteRatio = store.getInt("Setting", "TestMix", 30).coerceIn(0, 90)
        measureTime = store.getInt("Setting", "MeasureTime", 5).let { v ->
            if (v in MEASURE_TIMES) v else 5
        }
        intervalTime = store.getInt("Setting", "IntervalTime", 5).let { v ->
            if (v in INTERVAL_TIMES) v else 5
        }
        zoomType = store.getInt("Setting", "ZoomType", 200).let { v ->
            if (v in listOf(0, 100, 125, 150, 200, 250, 300)) v else 0
        }
        themeType = store.getInt("Setting", "ThemeType", 0).coerceIn(0, 2)
        fontFace = store.get("Setting", "FontFace", "").trim('"')
        fontScale = store.getInt("Setting", "FontScale", 100).coerceIn(50, 200)
        comment = store.get("Setting", "Comment", "")

        slots = (0 until 7).map { row ->
            val slot = settingsRowToSlot(row)
            BenchSetting(
                type = store.getInt("Setting", "BenchType$slot", DEFAULT_SETTINGS[row].type).coerceIn(0, 1),
                size = store.getInt("Setting", "BenchSize$slot", DEFAULT_SETTINGS[row].size).coerceIn(1, 8192),
                queues = store.getInt("Setting", "BenchQueues$slot", DEFAULT_SETTINGS[row].queues).coerceIn(1, MAX_QUEUES),
                threads = store.getInt("Setting", "BenchThreads$slot", DEFAULT_SETTINGS[row].threads).coerceIn(1, MAX_THREADS),
            )
        }
    }

    fun save() {
        store.setInt("Setting", "TestCount", testCountIndex)
        store.setInt("Setting", "TestSize", testSizeIndex)
        store.setInt("Setting", "TestUnit", testUnitIndex)
        store.setInt("Setting", "DriveLetter", testDriveLetter)
        store.set("Setting", "TargetPath", targetPath)
        store.setInt("Setting", "TestData", testData)
        store.setInt("Setting", "Profile", profile)
        store.setInt("Setting", "Benchmark", benchmark)
        store.setInt("Setting", "TestMix", mixWriteRatio)
        store.setInt("Setting", "MeasureTime", measureTime)
        store.setInt("Setting", "IntervalTime", intervalTime)
        store.setInt("Setting", "ZoomType", zoomType)
        store.setInt("Setting", "ThemeType", themeType)
        store.set("Setting", "FontFace", "\"$fontFace\"")
        store.setInt("Setting", "FontScale", fontScale)
        store.set("Setting", "Comment", comment)
        for (row in 0 until 7) {
            val slot = settingsRowToSlot(row)
            val s = slots[row]
            store.setInt("Setting", "BenchType$slot", s.type)
            store.setInt("Setting", "BenchSize$slot", s.size)
            store.setInt("Setting", "BenchQueues$slot", s.queues)
            store.setInt("Setting", "BenchThreads$slot", s.threads)
        }
        store.save()
    }

    // ------------------------------------------------------------------
    // Presets (Settings dialog buttons / Settings menu)
    // ------------------------------------------------------------------
    fun applyPreset(index: Int) {
        slots = when (index) {
            1 -> NVME8_SETTINGS.map { it.copy() }
            2 -> FLASH_MEMORY_SETTINGS.map { it.copy() }
            else -> DEFAULT_SETTINGS.map { it.copy() }
        }
        when (index) {
            0 -> { measureTime = 5; intervalTime = 5 }
            1 -> { measureTime = 5; intervalTime = 5 }
            2 -> { measureTime = 1; intervalTime = 30 }
        }
    }

    fun isDefaultPreset(): Boolean = matchesPreset(DEFAULT_SETTINGS, 5, 5)
    fun isNvme8Preset(): Boolean = matchesPreset(NVME8_SETTINGS, 5, 5)
    fun isFlashMemoryPreset(): Boolean = matchesPreset(FLASH_MEMORY_SETTINGS, 1, 30)

    private fun matchesPreset(preset: List<BenchSetting>, measure: Int, interval: Int): Boolean {
        if (measureTime != measure || intervalTime != interval) return false
        return slots.indices.all { i ->
            val a = slots[i]; val b = preset[i]
            a.type == b.type && a.size == b.size && a.queues == b.queues && a.threads == b.threads
        }
    }

    // ------------------------------------------------------------------
    // Drive helpers
    // ------------------------------------------------------------------
    fun refreshDrives() {
        drives = cn.enaium.crystaldiskmark.platform.listDrives()
        driveItems = drives.map { driveLabel(it) }
        if (testDriveLetter == 99) {
            testDriveIndex = driveItems.size
        } else {
            testDriveIndex = drives.indexOfFirst { it.path.startsWith(('A' + testDriveLetter).toString() + ":") }
                .let { if (it < 0) 0 else it }
        }
    }

    private fun driveLabel(d: DriveInfo): String {
        // Some volumes report no total space (statvfs/GetDiskFreeSpaceEx
        // failure); show the free space instead of a misleading "(x/0)".
        if (d.totalBytes <= 0) {
            val free = d.freeBytes.coerceAtLeast(0)
            return if (free < 8L * 1024 * 1024 * 1024) {
                Fmt.format("%s: %.0fMiB free", d.path, free / 1024.0 / 1024.0)
            } else {
                Fmt.format("%s: %.0fGiB free", d.path,
                    free / 1024.0 / 1024.0 / 1024.0)
            }
        }
        val used = (d.totalBytes - d.freeBytes).coerceAtLeast(0)
        val pct = used.toDouble() / d.totalBytes * 100
        return if (d.totalBytes < 8L * 1024 * 1024 * 1024) {
            Fmt.format("%s: %.0f%% (%.0f/%.0fMiB)", d.path, pct,
                used / 1024.0 / 1024.0, d.totalBytes / 1024.0 / 1024.0)
        } else {
            Fmt.format("%s: %.0f%% (%.0f/%.0fGiB)", d.path, pct,
                used / 1024.0 / 1024.0 / 1024.0, d.totalBytes / 1024.0 / 1024.0 / 1024.0)
        }
    }

    fun selectedDrivePath(): String {
        if (testDriveIndex in drives.indices) {
            return drives[testDriveIndex].path
        }
        return targetPath
    }

    fun engineConfig(): BenchmarkEngine.EngineConfig {
        return BenchmarkEngine.EngineConfig(
            testSizeMiB = testSizeMiB(),
            testCount = testCount(),
            testData = testData,
            profile = profile,
            benchmark = benchmark,
            mixEnabled = mixMode,
            mixWriteRatio = mixWriteRatio,
            measureTime = measureTime,
            intervalTime = intervalTime,
            targetPath = selectedDrivePath(),
            slots = (0 until 9).map { slot ->
                val row = slotToSettingsRow(slot)
                if (row >= 0) slots[row].copy() else BenchSetting()
            },
        )
    }
}
