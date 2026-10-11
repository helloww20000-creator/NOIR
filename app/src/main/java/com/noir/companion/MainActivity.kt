package com.noir.companion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.noir.companion.core.BleDeviceRepository
import com.noir.companion.core.NoirEventStore

/**
 * Functional first-pass control surface. The UI deliberately exposes real transport events,
 * not placeholder feature tiles. All BLE operations run through BleDeviceRepository.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var ble: BleDeviceRepository
    private lateinit var store: NoirEventStore
    private lateinit var logText: TextView
    private lateinit var deviceText: TextView
    private lateinit var statusText: TextView
    private lateinit var deviceList: LinearLayout
    private val lines = ArrayDeque<String>()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ble.hasScanPermission() && ble.hasConnectPermission()) startScan() else toast("Bluetooth permissions are required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ble = BleDeviceRepository(this)
        store = NoirEventStore(this)
        buildScreen()
        ble.addListener { event ->
            store.append(event)
            lines.addLast(event.toString())
            while (lines.size > 350) lines.removeFirst()
            logText.text = lines.joinToString("\n")
            if (event.category == "connect") statusText.text = event.message
            if (event.category == "device") renderDevices()
        }
        if (!ble.isBluetoothEnabled()) statusText.text = "Bluetooth is off"
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xFF101114.toInt())
        }
        val title = TextView(this).apply { text = "NOIR / DEVICE LAB"; textSize = 22f; setTextColor(0xFFFFFFFF.toInt()) }
        statusText = TextView(this).apply { text = "Ready • NSW-309 protocol workbench"; textSize = 13f; setTextColor(0xFFB6BBC5.toInt()); setPadding(0, dp(8), 0, dp(10)) }
        deviceText = TextView(this).apply { text = "Nearby devices"; textSize = 16f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0, dp(10), 0, dp(4)) }
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(actionButton("Scan", ::requestPermissionsAndScan))
        controls.addView(actionButton("Stop", { ble.stopScan() }))
        controls.addView(actionButton("Disconnect", { ble.disconnect() }))
        controls.addView(actionButton("Export log", ::exportLog))
        logText = TextView(this).apply {
            text = "Events will appear here."
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFFD7DBE2.toInt())
            setPadding(0, dp(10), 0, dp(10))
        }
        val logScroll = ScrollView(this).apply { addView(logText) }
        root.addView(title)
        root.addView(statusText)
        root.addView(controls)
        root.addView(deviceText)
        root.addView(deviceList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)))
        val logTitle = TextView(this).apply { text = "LIVE GATT / EVENT LOG"; textSize = 14f; setTextColor(0xFF8CC8FF.toInt()); setPadding(0, dp(12), 0, 0) }
        root.addView(logTitle)
        root.addView(logScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        setOnClickListener { action() }
        isAllCaps = false
    }

    private fun requestPermissionsAndScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.BLUETOOTH_CONNECT
        } else if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray()) else startScan()
    }

    private fun startScan() {
        if (Build.VERSION.SDK_INT < 31 && Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)); return
        }
        ble.startScan()
    }

    private fun renderDevices() {
        runOnUiThread {
            deviceList.removeAllViews()
            ble.devices().take(12).forEach { device ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val label = TextView(this).apply {
                    text = "${device.name}  ·  ${device.rssi} dBm"
                    textSize = 13f; setTextColor(0xFFE7EAF0.toInt())
                }
                row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(Button(this).apply {
                    text = "Connect"; textSize = 10f; isAllCaps = false
                    setOnClickListener { ble.connect(device.address) }
                })
                deviceList.addView(row)
            }
        }
    }

    private fun exportLog() {
        val text = store.exportText().ifBlank { "NOIR log is empty." }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "NOIR diagnostic log"); putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Export NOIR diagnostic log"))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        ble.stopScan()
        ble.disconnect()
        super.onDestroy()
    }
}
