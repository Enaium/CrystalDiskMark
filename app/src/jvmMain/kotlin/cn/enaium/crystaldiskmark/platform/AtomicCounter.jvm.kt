package cn.enaium.crystaldiskmark.platform

import java.util.concurrent.atomic.AtomicLong

class JvmAtomicCounter(private val value: AtomicLong) : AtomicCounter {
    override fun get(): Long = value.get()
    override fun addAndGet(delta: Long): Long = value.addAndGet(delta)
    override fun incrementAndGet(): Long = value.incrementAndGet()
}

actual fun atomicCounter(initial: Long): AtomicCounter = JvmAtomicCounter(AtomicLong(initial))
