/* Freestanding aarch64 outline-atomic helpers (GCC 9+ libgcc provides
 * these; Kotlin/Native's bundled GCC 8.3 sysroot does not). The sdl-kmp
 * linuxArm64 SDL3 static library is built with GCC and references them.
 */
typedef unsigned char uint8_t;
typedef unsigned int uint32_t;
typedef unsigned long uint64_t;

static inline uint64_t __atomic_load_8(volatile uint64_t *p) {
    return __atomic_load_n(p, __ATOMIC_SEQ_CST);
}

uint64_t __aarch64_ldadd4_acq_rel(uint32_t *p, uint32_t val) {
    return (uint64_t)__atomic_fetch_add(p, val, __ATOMIC_ACQ_REL);
}

uint64_t __aarch64_ldadd8_acq_rel(uint64_t *p, uint64_t val) {
    return __atomic_fetch_add(p, val, __ATOMIC_ACQ_REL);
}

uint64_t __aarch64_ldadd4_relax(uint32_t *p, uint32_t val) {
    return (uint64_t)__atomic_fetch_add(p, val, __ATOMIC_RELAXED);
}

uint64_t __aarch64_ldadd8_relax(uint64_t *p, uint64_t val) {
    return __atomic_fetch_add(p, val, __ATOMIC_RELAXED);
}

uint64_t __aarch64_ldclr4_acq_rel(uint32_t *p, uint32_t val) {
    return (uint64_t)__atomic_fetch_and(p, ~val, __ATOMIC_ACQ_REL);
}

uint64_t __aarch64_ldclr8_acq_rel(uint64_t *p, uint64_t val) {
    return __atomic_fetch_and(p, ~val, __ATOMIC_ACQ_REL);
}

uint64_t __aarch64_ldset4_acq_rel(uint32_t *p, uint32_t val) {
    return (uint64_t)__atomic_fetch_or(p, val, __ATOMIC_ACQ_REL);
}

uint64_t __aarch64_ldset8_acq_rel(uint64_t *p, uint64_t val) {
    return __atomic_fetch_or(p, val, __ATOMIC_ACQ_REL);
}

uint64_t __aarch64_cas4_acq_rel(uint32_t *p, uint32_t expected, uint32_t desired) {
    __atomic_compare_exchange_n(p, &expected, desired, 0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE);
    return expected;
}

uint64_t __aarch64_cas8_acq_rel(uint64_t *p, uint64_t expected, uint64_t desired) {
    __atomic_compare_exchange_n(p, &expected, desired, 0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE);
    return expected;
}

uint64_t __aarch64_cas4_relax(uint32_t *p, uint32_t expected, uint32_t desired) {
    __atomic_compare_exchange_n(p, &expected, desired, 0, __ATOMIC_RELAXED, __ATOMIC_RELAXED);
    return expected;
}

uint64_t __aarch64_cas8_relax(uint64_t *p, uint64_t expected, uint64_t desired) {
    __atomic_compare_exchange_n(p, &expected, desired, 0, __ATOMIC_RELAXED, __ATOMIC_RELAXED);
    return expected;
}
