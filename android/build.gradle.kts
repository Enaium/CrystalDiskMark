import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

plugins {
    alias(libs.plugins.android.application)
}

dependencies {
    // sdl-kmp 1.0.10 bundles SDL's Android Java layer
    // (org.libsdl.app.SDLActivity & co, via sdl-kmp-android-jvm AAR).
    implementation(libs.sdl.kmp)
}

val androidAbis = mapOf(
    "androidNativeArm64" to "arm64-v8a",
    "androidNativeArm32" to "armeabi-v7a",
    "androidNativeX64" to "x86_64",
    "androidNativeX86" to "x86",
)

/**
 * Copies the per-ABI `libmain.so` produced by the `:app` subproject's
 * `androidNative*` link tasks into AGP's jniLibs directory so the APK
 * ends up with the right native shared library in each ABI split.
 */
abstract class PrepareJniLibsTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val abis: MapProperty<String, String>

    /** Per-ABI libmain.so produced by the :app link tasks (content-hashed
     *  so a relinked library invalidates this task's outputs). */
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun run() {
        val bin = project.rootDir.toPath().resolve("app/build/bin").toFile()
        outputDir.get().asFile.deleteRecursively()
        abis.get().forEach { (target, abi) ->
            val src = File(bin, "$target/mainDebugShared/libmain.so")
            val dstDir = File(outputDir.get().asFile, abi)
            dstDir.mkdirs()
            src.copyTo(File(dstDir, "libmain.so"), overwrite = true)
        }
    }
}

val prepareJniLibs = tasks.register<PrepareJniLibsTask>("prepareJniLibs") {
    outputDir.set(layout.buildDirectory.dir("generated/jniLibs"))
    abis.set(androidAbis)
    sources.from(androidAbis.keys.map { File(rootDir, "app/build/bin/$it/mainDebugShared/libmain.so") })
}

prepareJniLibs.configure {
    androidAbis.keys.forEach { target ->
        val capitalized = target.replaceFirstChar { it.uppercase() }
        dependsOn(project(":app").tasks.named("linkMainDebugShared$capitalized"))
    }
}

android {
    namespace = "cn.enaium.crystaldiskmark"
    compileSdk = 36
    defaultConfig {
        applicationId = "cn.enaium.crystaldiskmark"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(prepareJniLibs) { it.outputDir }
    }
}
