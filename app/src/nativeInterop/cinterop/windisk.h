#define WIN32_LEAN_AND_MEAN
#include <windows.h>

/* ============ disk space ============ */

/* Return free bytes on the volume containing path, or -1 on error. */
static inline long long cdm_win_free(const char* path) {
    ULARGE_INTEGER freeBytesAvailable, totalBytes, totalFreeBytes;
    if (GetDiskFreeSpaceExA(path, &freeBytesAvailable, &totalBytes, &totalFreeBytes) == 0) {
        return -1;
    }
    return (long long)totalFreeBytes.QuadPart;
}

/* Return total bytes on the volume containing path, or -1 on error. */
static inline long long cdm_win_total(const char* path) {
    ULARGE_INTEGER freeBytesAvailable, totalBytes, totalFreeBytes;
    if (GetDiskFreeSpaceExA(path, &freeBytesAvailable, &totalBytes, &totalFreeBytes) == 0) {
        return -1;
    }
    return (long long)totalBytes.QuadPart;
}

/* Number of logical processors, or -1 on error. */
static inline long long cdm_win_cores(void) {
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    return (long long)si.dwNumberOfProcessors;
}

/* Total physical memory in bytes, or -1 on error. */
static inline long long cdm_win_memory(void) {
    MEMORYSTATUSEX ms;
    ms.dwLength = sizeof(ms);
    if (GlobalMemoryStatusEx(&ms) == 0) return -1;
    return (long long)ms.ullTotalPhys;
}

/* ============ file operations ============ */

/* Open (creating if needed) for read/write; returns HANDLE as long long. */
static inline long long cdm_win_open(const char* path, int create) {
    DWORD access = GENERIC_READ | GENERIC_WRITE;
    DWORD share = FILE_SHARE_READ | FILE_SHARE_WRITE;
    DWORD disp = create ? CREATE_ALWAYS : OPEN_EXISTING;
    HANDLE h = CreateFileA(path, access, share, NULL, disp, FILE_ATTRIBUTE_NORMAL, NULL);
    return (long long)(long)(h == INVALID_HANDLE_VALUE ? 0 : h);
}

static inline int cdm_win_set_length(long long handle, long long length) {
    HANDLE h = (HANDLE)(long)handle;
    LARGE_INTEGER li;
    li.QuadPart = length;
    return SetFilePointerEx(h, li, NULL, FILE_BEGIN) && SetEndOfFile(h) ? 0 : -1;
}

/* Reads up to len bytes at pos; returns bytes read or -1 on error. */
static inline long long cdm_win_read(long long handle, long long pos, void* buf, long long len) {
    HANDLE h = (HANDLE)(long)handle;
    LARGE_INTEGER li;
    li.QuadPart = pos;
    if (!SetFilePointerEx(h, li, NULL, FILE_BEGIN)) return -1;
    DWORD read = 0;
    if (!ReadFile(h, buf, (DWORD)len, &read, NULL)) return -1;
    return (long long)read;
}

/* Writes len bytes at pos; returns 0 on success, -1 on error. */
static inline int cdm_win_write(long long handle, long long pos, const void* buf, long long len) {
    HANDLE h = (HANDLE)(long)handle;
    LARGE_INTEGER li;
    li.QuadPart = pos;
    if (!SetFilePointerEx(h, li, NULL, FILE_BEGIN)) return -1;
    DWORD written = 0;
    if (!WriteFile(h, buf, (DWORD)len, &written, NULL)) return -1;
    return (long long)written == len ? 0 : -1;
}

static inline void cdm_win_close(long long handle) {
    CloseHandle((HANDLE)(long)handle);
}

static inline void cdm_win_delete(const char* path) {
    DeleteFileA(path);
}

/* Remove an empty directory. */
static inline void cdm_win_rmdir(const char* path) {
    RemoveDirectoryA(path);
}

static inline int cdm_win_exists(const char* path) {
    DWORD attr = GetFileAttributesA(path);
    return attr != INVALID_FILE_ATTRIBUTES ? 1 : 0;
}

static inline void cdm_win_mkdir(const char* path) {
    CreateDirectoryA(path, NULL);
}

/* Returns a newline-separated list of subdirectories, or NULL on error.
 * The caller must free the result with cdm_win_free_str. */
static inline char* cdm_win_list_dirs(const char* path) {
    char pattern[MAX_PATH + 8];
    if (strlen(path) + 3 > MAX_PATH) return NULL;
    strcpy(pattern, path);
    size_t n = strlen(pattern);
    if (n > 0 && (pattern[n - 1] == '\\' || pattern[n - 1] == '/')) {
        pattern[n - 1] = '\0';
    }
    strcat(pattern, "\\*");
    WIN32_FIND_DATAA fd;
    HANDLE h = FindFirstFileA(pattern, &fd);
    if (h == INVALID_HANDLE_VALUE) return NULL;
    char* out = NULL;
    size_t outLen = 0;
    size_t cap = 0;
    do {
        if ((fd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) &&
            strcmp(fd.cFileName, ".") != 0 && strcmp(fd.cFileName, "..") != 0) {
            size_t add = strlen(fd.cFileName) + 1; /* name + '\n' */
            if (outLen + add + 1 > cap) {
                size_t ncap = cap ? cap * 2 : 256;
                while (outLen + add + 1 > ncap) ncap *= 2;
                char* nout = (char*)realloc(out, ncap);
                if (!nout) { free(out); FindClose(h); return NULL; }
                out = nout;
                cap = ncap;
            }
            strcpy(out + outLen, fd.cFileName);
            outLen += strlen(fd.cFileName);
            out[outLen++] = '\n';
        }
    } while (FindNextFileA(h, &fd));
    FindClose(h);
    if (out) out[outLen] = '\0';
    return out;
}

static inline void cdm_win_free_str(char* s) {
    free(s);
}

/* File size in bytes, or -1 on error. */
static inline long long cdm_win_file_size(const char* path) {
    WIN32_FILE_ATTRIBUTE_DATA data;
    if (GetFileAttributesExA(path, GetFileExInfoStandard, &data) == 0) return -1;
    LARGE_INTEGER li;
    li.LowPart = data.nFileSizeLow;
    li.HighPart = data.nFileSizeHigh;
    return (long long)li.QuadPart;
}
