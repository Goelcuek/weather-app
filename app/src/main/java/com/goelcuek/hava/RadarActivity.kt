package com.goelcuek.hava

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import com.goelcuek.hava.data.Store
import java.util.Locale

/** Full-screen radar / wind / temperature map (assets/radar), hosted in a WebView. */
class RadarActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var insetTop = 0
    private var insetBottom = 0
    private var loaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        // Serve the bundled page from a real https origin so fetch()/CORS behave normally.
        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(0xFF111318.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportZoom(false)
            addJavascriptInterface(Bridge(), "HavaApp")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assets.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Attribution links etc. open in the browser; the map itself stays here.
                    if (!request.isForMainFrame) return false
                    val host = request.url.host ?: return false
                    if (host == "appassets.androidplatform.net") return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loaded = true
                    pushInsets()
                }
            }
        }
        setContentView(web)

        ViewCompat.setOnApplyWindowInsetsListener(web) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val d = resources.displayMetrics.density
            insetTop = (bars.top / d).toInt()
            insetBottom = (bars.bottom / d).toInt()
            pushInsets()
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        val s = Store(this)
        val url = Uri.parse("https://appassets.androidplatform.net/assets/radar/index.html").buildUpon()
            .appendQueryParameter("lat", s.lat.toString())
            .appendQueryParameter("lon", s.lon.toString())
            .appendQueryParameter("unit", s.unit)
            .appendQueryParameter("lang", Locale.getDefault().language)
            .appendQueryParameter("place", s.placeName)
            .build()
        web.loadUrl(url.toString())
    }

    private fun pushInsets() {
        if (loaded) web.evaluateJavascript("window.setInsets && setInsets($insetTop,$insetBottom)", null)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    inner class Bridge {
        @JavascriptInterface
        fun close() = runOnUiThread { finish() }
    }
}
