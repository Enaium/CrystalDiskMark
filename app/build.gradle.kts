import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

// Kotlin/Native's own Android toolchain sysroot (api 26) ships the NDK
// stub libraries that SDL3's android drivers reference at link time.
fun konanAndroidLibDir(abi: String): String? {
    val konanData = System.getenv("KONAN_DATA_DIR")
        ?: providers.gradleProperty("konan.data.dir").getOrElse("${System.getProperty("user.home")}/.konan")
    val toolchain = File(konanData, "dependencies").listFiles()
        ?.firstOrNull { it.isDirectory && it.name.matches(Regex("target-toolchain-.*-android_ndk")) }
        ?: return null
    val triple = when (abi) {
        "arm64-v8a" -> "aarch64-linux-android"
        "armeabi-v7a" -> "arm-linux-androideabi"
        "x86_64" -> "x86_64-linux-android"
        "x86" -> "i686-linux-android"
        else -> return null
    }
    val arch = when (abi) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a" -> "arm"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> return null
    }
    val depsDir = File(konanData, "dependencies")
    if (!depsDir.isDirectory) return null
    val markers = listOf(triple, "/arch-$arch/", "/$arch/usr/")
    val hit = depsDir.walkTopDown()
        .firstOrNull { f -> f.name == "libaaudio.so" && markers.any { f.path.contains(it) } }
    return hit?.parentFile?.absolutePath
}

// Locate the LLVM clang bundled with the Kotlin/Native toolchain.
fun konanClang(): File? {
    val konanData = System.getenv("KONAN_DATA_DIR")
        ?: providers.gradleProperty("konan.data.dir").getOrElse("${System.getProperty("user.home")}/.konan")
    return File(konanData, "tools").walkTopDown().firstOrNull { it.name == "clang" && it.isFile }
        ?: File(konanData, "dependencies").walkTopDown().firstOrNull { it.name == "clang" && it.isFile }
}

// The sdl-kmp linuxArm64 SDL3 static library references aarch64 libgcc
// outline-atomic helpers that K/N's sysroot does not provide.
val linuxArm64AtomicHelpersObj = layout.buildDirectory.file("generated/linuxArm64AtomicHelpers/atomic_helpers.o")
val linuxArm64AtomicHelpersSrc = file("src/linuxArm64Main/c/atomic_helpers.c")
val compileLinuxArm64AtomicHelpers = tasks.register("compileLinuxArm64AtomicHelpers") {
    inputs.file(linuxArm64AtomicHelpersSrc)
    outputs.file(linuxArm64AtomicHelpersObj)
    dependsOn("compileKotlinLinuxArm64")
    doLast {
        val clang = konanClang() ?: error(
            "Cannot locate the Kotlin/Native LLVM clang under ~/.konan. " +
            "Run a native compile first so the toolchain is downloaded."
        )
        val out = providers.exec {
            commandLine(
                clang.absolutePath,
                "--target=aarch64-unknown-linux-gnu",
                "-march=armv8-a",
                "-mno-outline-atomics",
                "-ffreestanding",
                "-O2",
                "-c",
                linuxArm64AtomicHelpersSrc.absolutePath,
                "-o",
                linuxArm64AtomicHelpersObj.get().asFile.absolutePath,
            )
        }.result.get()
        if (out.exitValue != 0) throw GradleException("clang failed (exit ${out.exitValue})")
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        mainRun {
            mainClass = "cn.enaium.crystaldiskmark.CrystalDiskMark"
        }
    }

    macosArm64 { binaries.executable() }
    macosX64 { binaries.executable() }

    linuxX64 { binaries.executable() }
    linuxArm64 {
        binaries.executable {
            linkerOpts(linuxArm64AtomicHelpersObj.get().asFile.absolutePath)
            linkTaskProvider.configure { dependsOn(compileLinuxArm64AtomicHelpers) }
        }
    }

    mingwX64 {
        binaries.executable {
            linkerOpts("-lhid", "-lsetupapi")
        }
    }

    iosArm64 { binaries.framework("CrystalDiskMark") }
    iosSimulatorArm64 { binaries.framework("CrystalDiskMark") }
    tvosArm64 { binaries.framework("CrystalDiskMark") }
    tvosSimulatorArm64 { binaries.framework("CrystalDiskMark") }

    // Android 15+ devices may use 16 KB memory pages; every shared object
    // must have its LOAD segments aligned to at least 16 KB (lld defaults
    // to 4 KB for the x86 and 32-bit ABIs).
    androidNativeArm64 {
        binaries.sharedLib("main") {
            konanAndroidLibDir("arm64-v8a")?.let { linkerOpts("-L$it") }
            linkerOpts("-Wl,--allow-multiple-definition")
            linkerOpts("-Wl,-z,max-page-size=16384")
        }
    }
    androidNativeArm32 {
        binaries.sharedLib("main") {
            konanAndroidLibDir("armeabi-v7a")?.let { linkerOpts("-L$it") }
            linkerOpts("-Wl,--allow-multiple-definition")
            linkerOpts("-Wl,-z,max-page-size=16384")
        }
    }
    androidNativeX64 {
        binaries.sharedLib("main") {
            konanAndroidLibDir("x86_64")?.let { linkerOpts("-L$it") }
            linkerOpts("-Wl,--allow-multiple-definition")
            linkerOpts("-Wl,-z,max-page-size=16384")
        }
    }
    androidNativeX86 {
        binaries.sharedLib("main") {
            konanAndroidLibDir("x86")?.let { linkerOpts("-L$it") }
            linkerOpts("-Wl,--allow-multiple-definition")
            linkerOpts("-Wl,-z,max-page-size=16384")
        }
    }

    // statvfs is not exposed by K/N's platform.posix on Apple/Linux;
    // declare it via a small cinterop. mingw uses GetDiskFreeSpaceExA
    // through the windisk cinterop instead.
    targets.withType<KotlinNativeTarget> {
        val targetName = this.name
        compilations.getByName("main") {
            cinterops {
                if (targetName != "mingwX64") {
                    create("statvfs") {
                        defFile(project.file("src/nativeInterop/cinterop/statvfs.def"))
                        includeDirs(project.file("src/nativeInterop/cinterop"))
                        compilerOpts("-D_XOPEN_SOURCE")
                    }
                } else {
                    create("windisk") {
                        defFile(project.file("src/nativeInterop/cinterop/windisk.def"))
                        includeDirs(project.file("src/nativeInterop/cinterop"))
                    }
                }
            }
        }
    }

    sourceSets {
        // ---------------------------------------------------------------
        // Dependency-platform matrix:
        //  - sysinfo-kmp: macosArm64, macosX64, linuxX64, linuxArm64,
        //    mingwX64, androidNative (NO iOS/tvOS)
        //  - filekit-core: macosArm64, linuxX64, linuxArm64, mingwX64,
        //    iosArm64, iosSimulatorArm64, android (NO macosX64, NO tvOS)
        //  - filekit-dialogs: macosArm64, mingwX64, iosArm64,
        //    iosSimulatorArm64 (NO linux, NO macosX64, NO tvOS, NO androidNative)
        // ---------------------------------------------------------------
        val nativeMain = create("nativeMain") {
            dependsOn(getByName("commonMain"))
        }
        // statvfs-based filesystem space (Apple/Linux/Android native; not mingw)
        val posixFsMain = create("posixFsMain") {
            dependsOn(nativeMain)
        }
        // POSIX file ops (pread/pwrite/opendir; Apple/Linux, not Android/mingw)
        val posixIoMain = create("posixIoMain") {
            dependsOn(nativeMain)
        }
        // Apple platforms (macOS/iOS/tvOS): POSIX + Foundation paths.
        val appleMain = create("appleMain") {
            dependsOn(posixIoMain)
            dependsOn(posixFsMain)
        }
        // FileKit dialogs: macosArm64, mingwX64, iosArm64, iosSimulatorArm64
        val filekitMain = create("filekitMain") {
            dependsOn(getByName("commonMain"))
            dependencies {
                implementation(libs.filekit.core)
                implementation(libs.filekit.dialogs)
            }
        }
        // sysinfo-kmp: everything except iOS/tvOS
        val sysinfoMain = create("sysinfoMain") {
            dependsOn(getByName("commonMain"))
            dependencies {
                implementation(libs.sysinfo.kmp)
            }
        }
        // SDL3 file dialogs: macosX64, linuxX64, linuxArm64, tvos, androidNative
        val sdlDialogsMain = create("sdlDialogsMain") {
            dependsOn(getByName("commonMain"))
        }
        // macosArm64 + mingwX64: FileKit dialogs + sysinfo
        val filekitSysinfoMain = create("filekitSysinfoMain") {
            dependsOn(filekitMain)
            dependsOn(sysinfoMain)
        }
        // macosX64 + linux: SDL dialogs + sysinfo
        val sdlSysinfoMain = create("sdlSysinfoMain") {
            dependsOn(sdlDialogsMain)
            dependsOn(sysinfoMain)
        }
        // iOS/tvOS: SDL dialogs, no sysinfo-kmp
        val appleSdlMain = create("appleSdlMain") {
            dependsOn(appleMain)
            dependsOn(sdlDialogsMain)
        }
        val androidMain = create("androidMain") {
            dependsOn(getByName("commonMain"))
            dependsOn(sysinfoMain)
            dependsOn(sdlDialogsMain)
            dependsOn(posixFsMain)
        }
        // 32-bit Android posix uses Int/UInt (arm32, x86).
        val androidPosix32Main = create("androidPosix32Main") {
            dependsOn(nativeMain)
        }

        // JVM: FileKit dialogs + sysinfo-kmp (JNI artifacts come
        // transitively from the libraries' jvm variants).
        val jvmMain = getByName("jvmMain")
        jvmMain.dependsOn(filekitSysinfoMain)

        getByName("macosArm64Main").apply {
            dependsOn(appleMain)
            dependsOn(filekitSysinfoMain)
        }
        getByName("macosX64Main").apply {
            dependsOn(appleMain)
            dependsOn(sdlSysinfoMain)
        }
        listOf("iosArm64", "iosSimulatorArm64").forEach { name ->
            getByName("${name}Main").dependsOn(appleSdlMain)
        }
        listOf("tvosArm64", "tvosSimulatorArm64").forEach { name ->
            getByName("${name}Main").dependsOn(appleSdlMain)
        }
        listOf("linuxX64", "linuxArm64").forEach { name ->
            getByName("${name}Main").apply {
                dependsOn(posixIoMain)
                dependsOn(posixFsMain)
                dependsOn(sdlSysinfoMain)
            }
        }
        getByName("mingwX64Main").apply {
            dependsOn(nativeMain)
            dependsOn(filekitSysinfoMain)
        }
        listOf("androidNativeArm64", "androidNativeX64").forEach { name ->
            getByName("${name}Main").apply {
                dependsOn(androidMain)
                dependsOn(posixIoMain)
            }
        }
        listOf("androidNativeArm32", "androidNativeX86").forEach { name ->
            getByName("${name}Main").apply {
                dependsOn(androidMain)
                dependsOn(androidPosix32Main)
            }
        }

        commonMain {
            dependencies {
                implementation(libs.sdl.kmp)
                implementation(libs.imgui.kmp)
                implementation(libs.kotlinx.coroutines.core)
            }
        }


    }
}

// macOS JVM needs the main thread to be the AppKit thread.
tasks.withType(JavaExec::class.java).configureEach {
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX && name == "jvmRun") {
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-XstartOnFirstThread")
    }
}
