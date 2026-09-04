@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package cn.enaium.crystaldiskmark.platform

class NativeAtomicCounter(private val value: kotlin.concurrent.atomics.AtomicLong) : AtomicCounter {
    override fun get(): Long = value.load()
    override fun addAndGet(delta: Long): Long = value.fetchAndAdd(delta) + delta
    override fun incrementAndGet(): Long = value.fetchAndAdd(1) + 1
}

actual fun atomicCounter(initial: Long): AtomicCounter =
    NativeAtomicCounter(kotlin.concurrent.atomics.AtomicLong(initial))
