package cn.enaium.crystaldiskmark

import android.content.pm.ActivityInfo
import org.libsdl.app.SDLActivity

/**
 * Launcher activity hosting the CrystalDiskMark SDL app.
 *
 * `SDLActivity` comes from sdl-kmp 1.0.10's Android AAR
 * (sdl-kmp-android-jvm bundles the org.libsdl.app Java layer); it loads
 * `libmain.so` and calls its exported `SDL_main` symbol on a dedicated
 * SDL thread. `libmain.so` is built by the :app module's androidNative*
 * link tasks and embeds the benchmark, imgui-kmp, sdl-kmp and
 * sysinfo-kmp together with a statically linked SDL3.
 */
class MainActivity : SDLActivity() {

    override fun getLibraries(): Array<String> = arrayOf("main")

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        SDLActivity.nativeSetenv("CDM_DPI_SCALE", density.toString())
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setWindowStyle(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setWindowStyle(true)
        }
    }
}
