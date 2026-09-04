package cn.enaium.crystaldiskmark

/**
 * Profile: benchmark slot layout.
 * Slot 0-3: Default profile (4 tests shown)
 * Slot 4-5: Peak profile (2 tests, each shown 3 ways: MB/s / IOPS / us)
 * Slot 6-7: Real profile (2 tests, each shown 3 ways)
 * Slot 8:   Demo profile (1 test)
 */
enum class Profile(val value: Int) {
    DEFAULT(0),
    PEAK(1),
    REAL(2),
    DEMO(3),
    DEFAULT_MIX(4),
    PEAK_MIX(5),
    REAL_MIX(6);

    val isMix: Boolean
        get() = this == DEFAULT_MIX || this == PEAK_MIX || this == REAL_MIX
}

enum class BenchmarkMode(val value: Int) {
    READ_ONLY(1),
    WRITE_ONLY(2),
    READ_WRITE(3)
}

enum class TestData(val value: Int) {
    RANDOM(0),
    ALL_0X00(1)
}

enum class BenchType(val value: Int) {
    SEQ(0),
    RND(1)
}

enum class ScoreUnit(val value: Int) {
    MBS(0),
    GBS(1),
    IOPS(2),
    US(3)
}

/** One benchmark slot: block size (KiB), queues, threads, access type. */
data class BenchSetting(
    var type: Int = 0,      // BenchType
    var size: Int = 1024,   // KiB
    var queues: Int = 8,
    var threads: Int = 1,
)

/** Presets for the Settings dialog rows: row 0..5 -> slots 0..5, row 6 -> slot 8. */
val DEFAULT_SETTINGS = listOf(
    BenchSetting(0, 1024, 8, 1),
    BenchSetting(0, 1024, 1, 1),
    BenchSetting(1, 4, 32, 1),
    BenchSetting(1, 4, 1, 1),
    BenchSetting(0, 1024, 8, 1),
    BenchSetting(1, 4, 32, 1),
    BenchSetting(0, 1024, 8, 1),
)

val NVME8_SETTINGS = listOf(
    BenchSetting(0, 1024, 8, 1),
    BenchSetting(0, 128, 32, 1),
    BenchSetting(1, 4, 32, 16),
    BenchSetting(1, 4, 1, 1),
    BenchSetting(0, 1024, 8, 1),
    BenchSetting(1, 4, 32, 16),
    BenchSetting(0, 1024, 8, 1),
)

val FLASH_MEMORY_SETTINGS = listOf(
    BenchSetting(0, 1024, 8, 1),
    BenchSetting(0, 1024, 1, 1),
    BenchSetting(1, 4, 32, 1),
    BenchSetting(1, 4, 1, 1),
    BenchSetting(0, 1024, 8, 1),
    BenchSetting(1, 4, 32, 1),
    BenchSetting(0, 1024, 8, 1),
)

/** Map a settings-dialog row (0..6) to benchmark slot (0..8). */
fun settingsRowToSlot(row: Int): Int = when (row) {
    0, 1, 2, 3 -> row
    4 -> 4
    5 -> 5
    6 -> 8
    else -> 0
}

fun slotToSettingsRow(slot: Int): Int = when (slot) {
    0, 1, 2, 3 -> slot
    4 -> 4
    5 -> 5
    8 -> 6
    else -> -1
}

const val SLOT_COUNT = 9
const val MAX_QUEUES = 512
const val MAX_THREADS = 64

/** One score cell. score in MB/s, latency in us. */
data class Score(
    var score: Double = 0.0,
    var latency: Double = 0.0,
)

data class MixSettings(
    var enabled: Boolean = false,
    /** write ratio in percent */
    var writeRatio: Int = 30,
)

/** Scores per slot (9 slots, matching the reference). */
class AppState {
    val readScore: Array<Score> = Array(SLOT_COUNT) { Score() }
    val writeScore: Array<Score> = Array(SLOT_COUNT) { Score() }
    val mixScore: Array<Score> = Array(SLOT_COUNT) { Score() }

    fun resetAll() {
        for (i in 0 until SLOT_COUNT) {
            readScore[i] = Score()
            writeScore[i] = Score()
            mixScore[i] = Score()
        }
    }
}

data class DriveInfo(
    val displayName: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
)

/**
 * Test file target: either a drive letter root (Windows) or a folder.
 * path is the directory where the test file will be created.
 */
data class TestTarget(
    val label: String,
    val path: String,
    val isFolder: Boolean,
)
