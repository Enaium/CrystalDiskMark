/*
 * AArch64 libgcc outline-atomic helpers.
 *
 * sdl-kmp's linuxArm64 SDL3 static library is built with GCC and references
 * the libgcc atomic helper functions (`__aarch64_cas4_sync` and friends) that
 * provide compare-and-swap / add / swap with `_sync` (seq_cst) semantics.
 * Kotlin/Native's bundled GCC 8.3 sysroot predates these helpers (they landed
 * in GCC 9 with the LSE out-of-line atomics work), so lld cannot resolve them.
 *
 * Provide the subset SDL3 actually uses, implemented with __atomic builtins.
 * K/N's own clang inlines these to ldaxr/stxr loops for armv8-a, so no further
 * runtime dependency is introduced. Return values match the libgcc ABI: the
 * value read before the operation (callers compare it against `expected`).
 */

int __aarch64_cas4_sync(int expected, int desired, int *ptr)
{
    int old = expected;
    __atomic_compare_exchange_n(ptr, &old, desired, 0,
                                __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
    return old;
}

long __aarch64_cas8_sync(long expected, long desired, long *ptr)
{
    long old = expected;
    __atomic_compare_exchange_n(ptr, &old, desired, 0,
                                __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST);
    return old;
}

int __aarch64_ldadd4_sync(int val, int *ptr)
{
    return __atomic_fetch_add(ptr, val, __ATOMIC_SEQ_CST);
}

int __aarch64_swp4_sync(int val, int *ptr)
{
    return __atomic_exchange_n(ptr, val, __ATOMIC_SEQ_CST);
}
