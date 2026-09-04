#include <sys/statvfs.h>

/* Return free bytes on the volume containing path, or -1 on error.
 * f_frsize (the fundamental block size) is the portable multiplier:
 * on APFS f_bsize is 1 MiB while f_frsize is 4 KiB, and using f_bsize
 * inflates the result 256x. */
static inline long long cdm_fs_free(const char* path) {
    struct statvfs buf;
    if (statvfs(path, &buf) != 0) return -1;
    return (long long)buf.f_bavail * (long long)buf.f_frsize;
}

/* Return total bytes on the volume containing path, or -1 on error. */
static inline long long cdm_fs_total(const char* path) {
    struct statvfs buf;
    if (statvfs(path, &buf) != 0) return -1;
    return (long long)buf.f_blocks * (long long)buf.f_frsize;
}
