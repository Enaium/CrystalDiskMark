package cn.enaium.crystaldiskmark.platform

import java.io.File
import java.io.RandomAccessFile as JvmRandomAccessFile

class JvmRandomAccessFileImpl(private val file: JvmRandomAccessFile) : RandomAccessFile {
    override fun setLength(length: Long) = file.setLength(length)

    override fun readAt(pos: Long, buffer: ByteArray, offset: Int, len: Int): Int {
        synchronized(file) {
            file.seek(pos)
            return file.read(buffer, offset, len)
        }
    }

    override fun writeAt(pos: Long, buffer: ByteArray, offset: Int, len: Int) {
        synchronized(file) {
            file.seek(pos)
            file.write(buffer, offset, len)
        }
    }

    override fun close() = file.close()
}

object JvmPlatformIo : PlatformIo {
    override fun openFile(path: String, create: Boolean): RandomAccessFile =
        JvmRandomAccessFileImpl(JvmRandomAccessFile(File(path), "rw"))

    override fun deleteFile(path: String) {
        val f = File(path)
        if (f.isDirectory) f.deleteRecursively() else f.delete()
    }

    override fun fileExists(path: String): Boolean = File(path).exists()

    override fun fileSize(path: String): Long = File(path).length()

    override fun createDirectory(path: String) {
        File(path).mkdirs()
    }

    override fun listDirectories(path: String): List<String> =
        File(path).listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    override fun spawnThread(name: String, block: () -> Unit): ThreadHandle {
        val t = Thread(block, name)
        t.isDaemon = true
        t.start()
        return object : ThreadHandle {
            override fun join() = t.join()
        }
    }

    override fun freeSpace(path: String): Long = File(path).usableSpace

    override fun totalSpace(path: String): Long = File(path).totalSpace

    override fun homeDir(): String = System.getProperty("user.home")

    override fun configDir(): String {
        val dir = File(homeDir(), ".CrystalDiskMark")
        dir.mkdirs()
        return dir.absolutePath
    }

    override fun osName(): String = System.getProperty("os.name") + " " + System.getProperty("os.version")

    override fun cpuName(): String =
        System.getenv("PROCESSOR_IDENTIFIER") ?: "CPU " + Runtime.getRuntime().availableProcessors() + " cores"

    override fun cpuCores(): Pair<Int, Int> {
        val logical = Runtime.getRuntime().availableProcessors()
        return Pair(logical, logical)
    }

    override fun totalMemory(): Long = Runtime.getRuntime().maxMemory()

    override fun readTextFile(path: String): String? {
        val f = File(path)
        if (!f.exists()) return null
        return f.readText(Charsets.UTF_8)
    }

    override fun writeTextFile(path: String, content: String) {
        File(path).writeText(content, Charsets.UTF_8)
    }

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun nanoTime(): Long = System.nanoTime()

    override fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    override fun log(message: String) = println(message)
}

actual fun platformIo(): PlatformIo = JvmPlatformIo
