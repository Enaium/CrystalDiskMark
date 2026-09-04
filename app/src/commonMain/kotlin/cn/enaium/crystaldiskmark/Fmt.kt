package cn.enaium.crystaldiskmark

import kotlin.math.round

/**
 * Minimal printf-style formatter for common code (Kotlin/Native has no
 * String.format). Supports the specifiers used by the port:
 * %s %4s %d %2d %3d %04d %02d %.Nf %9.Nf
 */
object Fmt {
    fun format(spec: String, vararg args: Any?): String {
        val sb = StringBuilder()
        var i = 0
        var arg = 0
        while (i < spec.length) {
            val c = spec[i]
            if (c == '%' && i + 1 < spec.length) {
                var j = i + 1
                // flags: '-', '0'
                var zeroPad = false
                if (spec[j] == '0') { zeroPad = true; j++ }
                // width
                var width = 0
                while (j < spec.length && spec[j].isDigit()) {
                    width = width * 10 + (spec[j] - '0')
                    j++
                }
                // precision
                var precision = -1
                if (j < spec.length && spec[j] == '.') {
                    j++
                    precision = 0
                    while (j < spec.length && spec[j].isDigit()) {
                        precision = precision * 10 + (spec[j] - '0')
                        j++
                    }
                }
                if (j < spec.length) {
                    val conv = spec[j]
                    if (conv == '%') {
                        sb.append('%')
                        i = j + 1
                        continue
                    }
                    val value = if (arg < args.size) args[arg] else null
                    arg++
                    val out = when (conv) {
                        's' -> value?.toString() ?: ""
                        'd' -> {
                            val n = when (value) {
                                is Int -> value.toLong()
                                is Long -> value
                                is Double -> value.toLong()
                                else -> 0L
                            }
                            val s = n.toString()
                            if (width > 0) {
                                if (zeroPad) s.padStart(width, '0') else s.padStart(width)
                            } else s
                        }
                        'f' -> {
                            val d = when (value) {
                                is Double -> value
                                is Float -> value.toDouble()
                                is Int -> value.toDouble()
                                is Long -> value.toDouble()
                                else -> 0.0
                            }
                            val p = if (precision >= 0) precision else 6
                            val neg = d < 0
                            val abs = if (neg) -d else d
                            val factor = pow10(p)
                            val scaled = round(abs * factor).toLong()
                            val intPart = scaled / factor
                            val fracPart = scaled % factor
                            val fracStr = fracPart.toString().padStart(p, '0')
                            var s = intPart.toString() + if (p > 0) "." + fracStr else ""
                            if (neg) s = "-" + s
                            if (width > 0 && s.length < width) s = " ".repeat(width - s.length) + s
                            s
                        }
                        else -> "%$conv"
                    }
                    sb.append(out)
                    i = j + 1
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun pow10(p: Int): Long {
        var r = 1L
        repeat(p) { r *= 10 }
        return r
    }
}
