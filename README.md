# CrystalDiskMark (Kotlin Multiplatform)

![](https://img.cdn1.vip/i/6a9a776d108bf_1788508013.webp)

A from-scratch Kotlin Multiplatform port of the classic
[CrystalDiskMark](https://crystalmark.info/) disk benchmark (Win32 C++
reference), sharing a single codebase across desktop, mobile and Android
Native targets. The UI replicates the reference layout while using the
imgui-kmp native dark style.

The benchmark engine is an original implementation (not a wrapper): it
creates a temporary test file, fills it with a deterministic pattern, and
measures sequential/random read & write throughput and latency across the
classic 9-slot profile (Q8T1/Q1T1/Q32T1/Q1T32 SEQ/RND, with Peak and Real
World profile variants).

## Supported Platforms

| Platform | Target | Artifact |
|---|---|---|
| Desktop (JVM) | `jvm` | runnable jar (`app/build/libs/app-jvm.jar`) |
| macOS | `macosArm64`, `macosX64` | native executable |
| Linux | `linuxX64`, `linuxArm64` | native executable |
| Windows | `mingwX64` | native executable (`.exe`) |
| iOS | `iosArm64`, `iosSimulatorArm64` | `CrystalDiskMark.framework` |
| tvOS | `tvosArm64`, `tvosSimulatorArm64` | `CrystalDiskMark.framework` |
| Android | `androidNativeArm64/Arm32/X64/X86` | APK with `libmain.so` per ABI |

(WebAssembly/JS is intentionally not targeted.)

## Requirements

- JDK 17+ (JVM target is 17)
- Android SDK (for the APK) with `sdk.dir` in `local.properties`
- Kotlin/Native toolchain: downloaded automatically on first native build
  (Kotlin 2.4.20; `~/.konan`)
- macOS/Linux host for Android Native builds (the NDK-based JNI/Android
  toolchain is not supported from a Windows host)

## Dependencies

- UI: [imgui-kmp](https://github.com/enaium/imgui-kmp) `1.0.16`
- Window/input/rendering: [sdl-kmp](https://github.com/enaium/sdl-kmp) `1.0.13`
- System info: [sysinfo-kmp](https://github.com/enaium/sysinfo-kmp) `1.0.1`
- File dialogs: FileKit `0.16.0`
- Kotlin `2.4.20`, AGP `9.4.1`, kotlinx-coroutines `1.11.0`

## Building

### Compile all 14 targets

```bash
./gradlew :app:compileKotlinJvm \
  :app:compileKotlinMacosArm64 :app:compileKotlinMacosX64 \
  :app:compileKotlinIosArm64 :app:compileKotlinIosSimulatorArm64 \
  :app:compileKotlinTvosArm64 :app:compileKotlinTvosSimulatorArm64 \
  :app:compileKotlinLinuxX64 :app:compileKotlinLinuxArm64 \
  :app:compileKotlinMingwX64 \
  :app:compileKotlinAndroidNativeArm64 :app:compileKotlinAndroidNativeArm32 \
  :app:compileKotlinAndroidNativeX64 :app:compileKotlinAndroidNativeX86
```

### JVM

```bash
./gradlew :app:jvmRun
```

The JNI natives live in the library's `-jni-jvm-*` variants resolved via
their module metadata (do not add them explicitly). `jvmRun` builds the
full runtime classpath automatically.

### macOS native

```bash
./gradlew :app:linkDebugExecutableMacosArm64
./app/build/bin/macosArm64/debugExecutable/app.kexe
```

### Android APK

```bash
./gradlew :android:assembleDebug
# -> android/build/outputs/apk/debug/android-debug.apk
```

The APK copies `libmain.so` from each `androidNative*` link task into the
per-ABI `jniLibs` directories (arm64-v8a, armeabi-v7a, x86_64, x86).
The launcher `MainActivity` extends `org.libsdl.app.SDLActivity`, which
comes from the sdl-kmp Android AAR (`sdl-kmp-android-jvm` bundles SDL's
Java layer + `libsdl_jni.so`) — no vendored SDL Java code in this repo.

## Running

The native executables read their configuration from environment
variables; the JVM entry point accepts the same options as CLI arguments:

| Option (JVM) | Env var (native) | Effect |
|---|---|---|
| `--frames N` | `IMGUI_KMP_FRAMES` | exit after N frames (headless/CI) |
| `--width W` / `--height H` | `CDM_WIDTH` / `CDM_HEIGHT` | initial window size |
| `--smoke` | `CDM_SMOKE=1` | run the engine smoke test, print scores, exit |
| `--click-test [X Y]` | `CDM_CLICK_TEST=1` | inject a click on the All button, print `CLICK_TEST result=PASS/FAIL` |
| `--screenshot PATH` | `CDM_SCREENSHOT=<path>` | save a PNG of frame 5 (JVM) / mid-run under click-test |

Examples:

```bash
# Smoke test (JVM)
./gradlew :app:jvmRun --args="--smoke"

# Smoke test (macOS native, headless)
CDM_SMOKE=1 ./app/build/bin/macosArm64/debugExecutable/app.kexe

# Click test (JVM, headless)
SDL_VIDEO_DRIVER=dummy ./gradlew :app:jvmRun --args="--click-test"

# Click test (native, headless)
SDL_VIDEO_DRIVER=dummy CDM_CLICK_TEST=1 ./app/build/bin/macosArm64/debugExecutable/app.kexe
```

Under `--click-test` the benchmark is reconfigured to a small fast run
(16 MiB, 1 pass, 1 s/slot, no interval) so the disabled-button state is
visible on the mid-run screenshot and the verdict lands while the run is
still active.

## Project Layout

```
app/src/commonMain/kotlin/cn/enaium/crystaldiskmark/
├── Main.kt                # frame loop, events, screenshot/click-test hooks
├── Settings.kt            # persisted settings + zoom + profiles
├── Lang.kt                # 11 languages (en/ja/zh-CN/zh-TW/ko/fr/de/es/it/pt/ru)
├── Model.kt               # benchmark state & score model
├── ConfigStore.kt         # INI-style config store
├── engine/BenchmarkEngine.kt  # original benchmark engine (seq/rnd, 9 slots)
├── ui/                    # MainWindowUi, SettingsDialogUi, AboutDialogUi, FontDialogUi
└── platform/              # expect/actual: file IO, system info, dialogs, drives
```

Platform source sets (`app/build.gradle.kts`) layer the libraries by
platform matrix: `filekitSysinfoMain` (macosArm64+mingw+JVM),
`sdlSysinfoMain` (macosX64+linux), `appleSdlMain` (iOS/tvOS),
`androidMain`+`posixIoMain`, plus cinterops for `statvfs`, `pread/pwrite`
and the Win32 disk API (`windisk.def`).

## Notes

- **Fonts**: the app loads only the glyphs the UI actually needs
  (all translated strings + ASCII/Latin-1 + CJK punctuation) via
  `ImFontGlyphRangesBuilder`, and caps `rasterizerDensity` at 1.25x.
  Loading the full CJK ideograph block balloons the atlas past GPU texture
  limits on Retina and drops glyphs (`?`).
- **CJK font selection**: candidates are tried in platform order and the
  outline format is sniffed before loading (`fontHasStbOutlines`).
  imgui's stb rasterizer can only outline TrueType (`glyf`) and CFF1
  (`CFF `) fonts; CFF2-only collections — Noto Sans CJK ships as one on
  Android 14+ (`/system/fonts/NotoSansCJK-Regular.ttc`, 5 CFF2 faces) —
  make `ImFontAtlas` silently fall back to its 13px built-in font, which
  renders Chinese as tiny `?` marks. TrueType-flavoured files therefore
  come first (`MiSansVF.ttf` on Xiaomi, `PingFang.ttc`/`STHeiti Light.ttc`
  on Apple, `msyh.ttc` on Windows, `wqy-microhei.ttc` on Linux) and the
  Noto collections are kept as a last resort, so a device that only ships
  CFF2 fonts still falls back to the correctly-sized built-in font
  instead of a 13px one. Loading a font at runtime on such a device means
  bundling a TrueType CJK font.
- **linuxArm64**: SDL3's static library references aarch64 libgcc
  outline-atomic helpers; a small `atomic_helpers.c` is compiled and
  linked automatically by the build.
- **Settings** are persisted to the config directory
  (`~/.CrystalDiskMark/CrystalDiskMark.ini`). Delete it to reset.
- **Zoom** (100–300% + auto) scales layout and fonts together, mirroring
  the reference `m_ZoomRatio` behavior; the default is 200%.

## License

MIT — see [LICENSE](LICENSE). CrystalDiskMark is a trademark of its
respective owners; this project is an independent reimplementation.
