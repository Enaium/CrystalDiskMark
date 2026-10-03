package cn.enaium.crystaldiskmark

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowInsets
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

    private var lastSafeInsets: IntArray? = null

    override fun getLibraries(): Array<String> = arrayOf("main")

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        SDLActivity.nativeSetenv("CDM_DPI_SCALE", density.toString())
        // Android app processes have no HOME; the native layer uses it for
        // the config dir and the default benchmark target. Provide the
        // private files dir (always writable). Safe here: the SDL thread
        // (and thus SDL_main) only starts once the activity is RESUMED.
        SDLActivity.nativeSetenv("HOME", filesDir.absolutePath)
        // Report irregular-screen insets (punch hole, rounded corners,
        // visible system bars) to the native layer so the UI is laid out
        // inside the safe area. Checked before every draw; the env vars
        // are only written when the values change.
        window.decorView.viewTreeObserver.addOnPreDrawListener {
            publishSafeInsets(density)
            true
        }
    }

    /**
     * Publishes the window's safe insets in screen pixels as the
     * CDM_SAFE_* environment variables. Uses the same categories the SDL
     * surface reports (system bars + display cutout); the rounded display
     * corners are covered by a minimum 12dp clearance.
     */
    private fun publishSafeInsets(density: Float) {
        val root = window.decorView.rootWindowInsets
        var left = 0
        var right = 0
        var top = 0
        var bottom = 0
        if (root != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                val insets = root.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                left = insets.left
                right = insets.right
                top = insets.top
                bottom = insets.bottom
            } else {
                @Suppress("DEPRECATION")
                val insets = root.systemWindowInsets
                val cutout = root.displayCutout
                left = maxOf(insets.left, cutout?.safeInsetLeft ?: 0)
                right = maxOf(insets.right, cutout?.safeInsetRight ?: 0)
                top = maxOf(insets.top, cutout?.safeInsetTop ?: 0)
                bottom = maxOf(insets.bottom, cutout?.safeInsetBottom ?: 0)
            }
        }
        val corner = (12 * density).toInt()
        val values = intArrayOf(
            maxOf(left, corner),
            maxOf(right, corner),
            maxOf(top, corner),
            maxOf(bottom, corner),
        )
        if (lastSafeInsets?.contentEquals(values) != true) {
            lastSafeInsets = values
            SDLActivity.nativeSetenv("CDM_SAFE_LEFT", values[0].toString())
            SDLActivity.nativeSetenv("CDM_SAFE_RIGHT", values[1].toString())
            SDLActivity.nativeSetenv("CDM_SAFE_TOP", values[2].toString())
            SDLActivity.nativeSetenv("CDM_SAFE_BOTTOM", values[3].toString())
        }
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
