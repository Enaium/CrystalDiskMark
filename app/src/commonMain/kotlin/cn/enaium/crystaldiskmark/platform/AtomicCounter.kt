package cn.enaium.crystaldiskmark.platform

/**
 * Minimal atomic 64-bit counter used by the benchmark engine.
 * JVM: java.util.concurrent.atomic.AtomicLong
 * Native: kotlin.concurrent.atomics.AtomicLong (K/N common stdlib)
 */
interface AtomicCounter {
    fun get(): Long
    fun addAndGet(delta: Long): Long
    fun incrementAndGet(): Long
}

expect fun atomicCounter(initial: Long): AtomicCounter
