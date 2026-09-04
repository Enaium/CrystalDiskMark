package cn.enaium.crystaldiskmark

import cn.enaium.crystaldiskmark.platform.platformIo

/**
 * INI-style settings store (sections + key=value), mirroring the
 * reference's GetPrivateProfileString/WritePrivateProfileString.
 * Persisted as CrystalDiskMark.ini in the config dir.
 */
class ConfigStore(private val io: cn.enaium.crystaldiskmark.platform.PlatformIo = platformIo()) {

    private val file: String
        get() = io.configDir() + "/CrystalDiskMark.ini"

    private var data: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    fun load() {
        data = mutableMapOf()
        val content = io.readTextFile(file) ?: return
        var section = ""
        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.substring(1, trimmed.length - 1)
                continue
            }
            val idx = trimmed.indexOf('=')
            if (idx <= 0) continue
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            data.getOrPut(section) { mutableMapOf() }[key] = value
        }
    }

    fun save() {
        val sb = StringBuilder()
        for ((section, entries) in data) {
            sb.append("[$section]\n")
            for ((key, value) in entries) {
                sb.append(key).append('=').append(value).append('\n')
            }
            sb.append('\n')
        }
        io.writeTextFile(file, sb.toString())
    }

    operator fun get(section: String, key: String, default: String = ""): String =
        data[section]?.get(key) ?: default

    operator fun set(section: String, key: String, value: String) {
        data.getOrPut(section) { mutableMapOf() }[key] = value
    }

    fun getInt(section: String, key: String, default: Int): Int =
        get(section, key).toIntOrNull() ?: default

    fun setInt(section: String, key: String, value: Int) = set(section, key, value.toString())
}
