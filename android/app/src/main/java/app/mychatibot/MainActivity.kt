package app.mychatibot

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import app.mychatibot.eyes.EyeBrainHost
import app.mychatibot.eyes.EyeControllerHost
import app.mychatibot.eyes.IntentSource

class MainActivity : Activity() {

    private val isDebuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var debugOverlay: TextView
    private lateinit var statusOverlay: TextView
    private val eyeHost = EyeControllerHost.instance
    private var debugReceiver: BroadcastReceiver? = null

    private val statsLogger = object : Runnable {
        override fun run() {
            Log.i(TAG, "stats ${NativeBridge.nativeStats()}")
            mainHandler.postDelayed(this, STATS_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        // Use the whole panel, including the area beside the camera cutout.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val info = "Chati Eyes ${appVersionName()}\n${NativeBridge.nativeVersion()}"
        Log.i(TAG, info)
        NativeBridge.nativeStart()
        EyeBrainHost.instance.init(this)  // loads the eye model in the background

        // 4:3 surface (the upstream example's aspect ratio), centered; the native renderer
        // fills it with the original Monochrome Blue eyes.
        val surfaceOwner = NativeBridge.newSurfaceOwner()
        val surface = SurfaceView(this).apply {
            id = View.generateViewId()
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) =
                    NativeBridge.nativeAttachSurface(holder.surface, surfaceOwner)
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                    NativeBridge.nativeAttachSurface(holder.surface, surfaceOwner)
                override fun surfaceDestroyed(holder: SurfaceHolder) = NativeBridge.nativeDetachSurface(surfaceOwner)
            })
        }
        debugOverlay = TextView(this).apply {
            text = info
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            visibility = if (isDebuggable) View.VISIBLE else View.GONE
        }
        // Controller status: last input, validation result, applied intent, mode.
        statusOverlay = TextView(this).apply {
            text = "mode=idle"
            setTextColor(Color.rgb(0x00, 0xBF, 0xFF))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            val pad = (8 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            visibility = if (isDebuggable) View.VISIBLE else View.GONE
        }
        eyeHost.setStatusListener(this) { status ->
            runOnUiThread { if (!isDestroyed) statusOverlay.text = status }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(0, 0, Gravity.CENTER))
            addView(debugOverlay, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            addView(statusOverlay, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START))
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> fitAspect(surface, v.width, v.height) }
            // Keep the debugOverlay below the camera cutout.
            setOnApplyWindowInsetsListener { _, insets ->
                val top = insets.displayCutout?.safeInsetTop ?: 0
                (debugOverlay.layoutParams as FrameLayout.LayoutParams).topMargin = top
                debugOverlay.requestLayout()
                insets
            }
        }
        setContentView(root)
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        eyeHost.resume()
        if (isDebuggable) {
            registerDebugReceiver()
            mainHandler.post(statsLogger)
        }
    }

    override fun onPause() {
        mainHandler.removeCallbacks(statsLogger)
        debugReceiver?.let { unregisterReceiver(it) }
        debugReceiver = null
        eyeHost.pause()
        super.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The model is ~0.6 GB of memory; give it back when the app is in the background and
        // the system asks. The next sentence reloads it.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) EyeBrainHost.instance.release()
    }

    override fun onDestroy() {
        eyeHost.clearStatusListener(this)  // the process-wide controller outlives this activity
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Sizes the surface to the largest centered 4:3 rectangle inside the given area. */
    private fun fitAspect(surface: SurfaceView, width: Int, height: Int) {
        if (width == 0 || height == 0) return
        val w: Int
        val h: Int
        if (width * 3 <= height * 4) {
            w = width; h = width * 3 / 4
        } else {
            h = height; w = height * 4 / 3
        }
        val lp = surface.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            surface.post { surface.layoutParams = lp }
        }
    }

    /**
     * Development/test channel only (debuggable builds; not the final interaction), e.g.:
     * ```
     * # raw engine commands (bypass the controller)
     * adb shell am broadcast -a app.mychatibot.DEBUG_EYES --es expr HAPPY --es look "-0.8,0" --ez blink true
     * # a full intent through the validator and EyeController
     * adb shell am broadcast -a app.mychatibot.DEBUG_INTENT --es json '{"schema_version":1,"expression":"SAD","look":"down"}'
     * # a sentence for the on-phone eye model (Gemma 3 1B) → validator → controller
     * adb shell am broadcast -a app.mychatibot.DEBUG_SAY --es text "I got the job!"
     * ```
     * # benchmark only: switch eye model file / thread counts
     * adb shell am broadcast -a app.mychatibot.DEBUG_BRAIN --es model gemma-3-1b-it-qat-Q4_0.gguf --ei threads 2 --ei threads_batch 4
     * `--ez overlay false|true` hides/shows both overlays; `--ez stats true` logs renderer counters.
     */
    private fun registerDebugReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    DEBUG_INTENT_ACTION -> intent.getStringExtra("json")
                        ?.let { eyeHost.submitJson(it, IntentSource.DEBUG) }
                        ?: Log.w(TAG, "debug: DEBUG_INTENT without json extra")
                    DEBUG_BRAIN_ACTION -> EyeBrainHost.instance.configure(
                        intent.getStringExtra("model"),
                        intent.getIntExtra("threads", 0).takeIf { it > 0 },
                        intent.getIntExtra("threads_batch", 0).takeIf { it > 0 },
                    )
                    DEBUG_SAY_ACTION -> intent.getStringExtra("text")
                        ?.let { EyeBrainHost.instance.submitSentence(it) }
                        ?: Log.w(TAG, "debug: DEBUG_SAY without text extra")
                    else -> handleRawEngineCommand(intent)
                }
                if (intent.getBooleanExtra("stats", false)) Log.i(TAG, "stats ${NativeBridge.nativeStats()}")
                if (intent.hasExtra("overlay")) {
                    val visibility = if (intent.getBooleanExtra("overlay", true)) View.VISIBLE else View.GONE
                    debugOverlay.visibility = visibility
                    statusOverlay.visibility = visibility
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(DEBUG_ACTION)
            addAction(DEBUG_INTENT_ACTION)
            addAction(DEBUG_SAY_ACTION)
            addAction(DEBUG_BRAIN_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        debugReceiver = receiver
    }

    private fun handleRawEngineCommand(intent: Intent) {
        intent.getStringExtra("expr")?.let { name ->
            val id = NativeBridge.EXPRESSIONS.indexOf(name.uppercase())
            if (id >= 0) NativeBridge.nativeSetExpression(id) else Log.w(TAG, "debug: unknown expr $name")
            Log.i(TAG, "debug: expr $name -> $id")
        }
        intent.getStringExtra("look")?.let { look ->
            val parts = look.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size == 2) {
                NativeBridge.nativeLookAt(parts[0], parts[1])
                Log.i(TAG, "debug: look ${parts[0]},${parts[1]}")
            } else {
                Log.w(TAG, "debug: bad look $look")
            }
        }
        if (intent.getBooleanExtra("blink", false)) {
            NativeBridge.nativeBlink()
            Log.i(TAG, "debug: blink")
        }
    }

    private fun appVersionName(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private companion object {
        const val TAG = "ChatiEyes"
        const val DEBUG_ACTION = "app.mychatibot.DEBUG_EYES"
        const val DEBUG_INTENT_ACTION = "app.mychatibot.DEBUG_INTENT"
        const val DEBUG_SAY_ACTION = "app.mychatibot.DEBUG_SAY"
        const val DEBUG_BRAIN_ACTION = "app.mychatibot.DEBUG_BRAIN"
        const val STATS_INTERVAL_MS = 10_000L
    }
}
