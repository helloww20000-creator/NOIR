package com.noir.companion

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var ble: NativeBleBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()
        ble = NativeBleBridge(this) { js -> runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(js, null) } }
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                try {
                    val shim = assets.open("noir-ble-shim.js").bufferedReader().use { it.readText() }
                    view.evaluateJavascript(shim, null)
                } catch (_: Exception) { }
            }
        }
        web.addJavascriptInterface(ble, "NoirNativeBLE")
        setContentView(web)
        web.loadUrl("file:///android_asset/dafit/index.html")
    }

    private fun requestBlePermissions() {
        val wanted = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = wanted.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::ble.isInitialized) ble.close()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}