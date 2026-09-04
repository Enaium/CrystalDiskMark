@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package cn.enaium.crystaldiskmark

import cn.enaium.sdl.SDL
import kotlin.native.CName
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ByteVar

/**
 * SDL3 Android entry point: SDLActivity loads libmain.so and calls the
 * exported SDL_main symbol. SDL was already initialized by the Java
 * activity, so we just run the same runCrystalDiskMark as everywhere else.
 */
@CName("SDL_main")
fun sdlMain(argc: Int, argv: CPointer<CPointerVar<ByteVar>>?): Int {
    runCrystalDiskMark(width = 480, height = 300)
    SDL.quit()
    return 0
}
