package com.noir.companion

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var ble: NativeBleBridge
    private lateinit var assetLoader: WebViewAssetLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()

        ble = NativeBleBridge(this) { js ->
            runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(js, null) }
        }
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.e(TAG, "WebView JS ${message.messageLevel()}: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return interceptAssetRequest(request.url) ?: super.shouldInterceptRequest(view, request)
            }

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
                return interceptAssetRequest(Uri.parse(url)) ?: super.shouldInterceptRequest(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.i(TAG, "WebView page loaded: $url")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "WebView load failed (${error.errorCode}): ${error.description}; ${request.url}")
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(
                        APP_ASSETS_ORIGIN,
                        LOAD_ERROR_HTML,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        }
        web.addJavascriptInterface(ble, "NoirNativeBLE")
        setContentView(web)
        web.loadUrl("$APP_ASSETS_ORIGIN/")
    }

    private fun interceptAssetRequest(uri: Uri): WebResourceResponse? {
        if (uri.scheme == "https" && uri.host == APP_ASSETS_HOST) {
            val path = uri.path.orEmpty()
            val isSpaRoute = path == "/" ||
                (path.startsWith("/") && !path.startsWith("/api/") && !path.substringAfterLast('/').contains('.'))
            if (isSpaRoute) return WebResourceResponse("text/html", "UTF-8", assets.open("index.html"))
        }
        return assetLoader.shouldInterceptRequest(uri)
    }

    private fun requestBlePermissions() {
        val wanted = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::ble.isInitialized) ble.close()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NOIR"
        private const val APP_ASSETS_ORIGIN = "https://appassets.androidplatform.net"
        private const val APP_ASSETS_HOST = "appassets.androidplatform.net"
        private val LOAD_ERROR_HTML = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="light dark">
            <body style="margin:0;background:#111;color:#fff;font:16px system-ui;padding:28px;line-height:1.5">
              <h2>NOIR could not load its bundled editor</h2>
              <p>The app's local web assets did not load. Reinstall the latest NOIR APK and try again.</p>
            </body>
        """.trimIndent()
    }
}
