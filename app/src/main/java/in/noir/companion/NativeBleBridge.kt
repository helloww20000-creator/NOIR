package com.noir.companion

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.noir.companion.core.WatchFaceUploadGuard
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@SuppressLint("MissingPermission")
class NativeBleBridge(private val context: Context, private val emitJs: (String) -> Unit) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = manager.adapter
    private var gatt: BluetoothGatt? = null
    private var scannerCallback: ScanCallback? = null
    private var connectRequestId: String? = null
    private var connectedName: String = "Unknown device"
    private var connectedAddress: String = ""
    private val main = Handler(Looper.getMainLooper())
    private fun uuid(value: String): UUID = UUID.fromString(if (value.length == 4) "0000${value}-0000-1000-8000-00805f9b34fb" else value)
    private fun jsCall(name: String, json: JSONObject) { emitJs("window.__noirBleEvent && window.__noirBleEvent(" + JSONObject.quote(name) + "," + json.toString() + ");") }
    private fun resolve(id: String, json: JSONObject) { emitJs("window.__noirBleResolve && window.__noirBleResolve(" + JSONObject.quote(id) + "," + json.toString() + ");") }

    @JavascriptInterface fun scan(id: String) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) { resolve(id, JSONObject().put("error", "Bluetooth is disabled")); return }
        val devices = JSONArray()
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val d = result.device
                val name = result.scanRecord?.deviceName ?: try { d.name } catch (_: Exception) { null } ?: "Unknown device"
                val item = JSONObject().put("id", d.address).put("name", name).put("rssi", result.rssi)
                synchronized(devices) {
                    var found = false
                    for (i in 0 until devices.length()) if (devices.optJSONObject(i)?.optString("id") == d.address) found = true
                    if (!found) devices.put(item)
                }
                jsCall("device", item)
            }
            override fun onScanFailed(errorCode: Int) { jsCall("scanError", JSONObject().put("code", errorCode)) }
        }
        scannerCallback = cb
        try {
            scanner.startScan(cb)
            main.postDelayed({
                try { scanner.stopScan(cb) } catch (_: Exception) {}
                scannerCallback = null
                resolve(id, JSONObject().put("devices", devices))
            }, 10000)
        } catch (e: Exception) { resolve(id, JSONObject().put("error", e.message ?: "Scan failed")) }
    }

    @JavascriptInterface fun connect(id: String, address: String) {
        try {
            val device = adapter?.getRemoteDevice(address) ?: throw IllegalStateException("Device unavailable")
            connectRequestId = id
            connectedAddress = address
            connectedName = device.name ?: "Unknown device"
            gatt?.close()
            gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        gatt = g
                        g.discoverServices()
                        jsCall("connected", JSONObject().put("id", address).put("name", connectedName))
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        jsCall("disconnected", JSONObject().put("id", address).put("status", status))
                    }
                }
                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    jsCall("services", JSONObject().put("status", status))
                    val request = connectRequestId
                    if (request != null) {
                        connectRequestId = null
                        if (status == BluetoothGatt.GATT_SUCCESS) resolve(request, JSONObject().put("connected", true).put("id", connectedAddress).put("name", connectedName))
                        else resolve(request, JSONObject().put("error", "Service discovery failed: " + status))
                    }
                }
                @Deprecated("Android 13")
                override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                    jsCall("read", JSONObject().put("uuid", c.uuid.toString()).put("hex", c.value?.joinToString("") { "%02x".format(it) } ?: "").put("status", status))
                }
                override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                    jsCall("notify", JSONObject().put("uuid", c.uuid.toString()).put("hex", c.value?.joinToString("") { "%02x".format(it) } ?: ""))
                }
            }, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) { resolve(id, JSONObject().put("error", e.message ?: "Connect failed")) }
    }

    @JavascriptInterface fun listServices(id: String) {
        val out = JSONArray()
        gatt?.services?.forEach { s ->
            val chars = JSONArray()
            s.characteristics.forEach { c -> chars.put(JSONObject().put("uuid", c.uuid.toString()).put("properties", c.properties)) }
            out.put(JSONObject().put("uuid", s.uuid.toString()).put("characteristics", chars))
        }
        resolve(id, JSONObject().put("services", out))
    }

    @JavascriptInterface fun read(id: String, serviceId: String, charId: String) {
        val c = gatt?.getService(uuid(serviceId))?.getCharacteristic(uuid(charId))
        if (c == null) { resolve(id, JSONObject().put("error", "Characteristic not found")); return }
        if (gatt?.readCharacteristic(c) != true) resolve(id, JSONObject().put("error", "Read request rejected"))
        else resolve(id, JSONObject().put("pending", true))
    }

    @JavascriptInterface fun write(id: String, serviceId: String, charId: String, hex: String, withoutResponse: Boolean) {
        try {
            val g = gatt ?: throw IllegalStateException("Not connected")
            val c = g.getService(uuid(serviceId))?.getCharacteristic(uuid(charId)) ?: throw IllegalStateException("Characteristic not found")
            val bytes = hex.chunked(2).filter { it.length == 2 }.map { it.toInt(16).toByte() }.toByteArray()
            if (WatchFaceUploadGuard.shouldBlock(serviceId, charId, bytes)) {
                throw IllegalStateException("Watch-face upload is disabled until its transfer and acknowledgement behavior are verified on NSW-309.")
            }
            if (Build.VERSION.SDK_INT >= 33) {
                val status = g.writeCharacteristic(c, bytes, if (withoutResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                if (status != BluetoothGatt.GATT_SUCCESS) throw IllegalStateException("Write rejected: " + status)
            } else {
                @Suppress("DEPRECATION") c.value = bytes
                @Suppress("DEPRECATION") c.writeType = if (withoutResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION") if (!g.writeCharacteristic(c)) throw IllegalStateException("Write rejected")
            }
            resolve(id, JSONObject().put("ok", true))
        } catch (e: Exception) { resolve(id, JSONObject().put("error", e.message ?: "Write failed")) }
    }

    @JavascriptInterface fun notifications(id: String, serviceId: String, charId: String, enable: Boolean) {
        try {
            val g = gatt ?: throw IllegalStateException("Not connected")
            val c = g.getService(uuid(serviceId))?.getCharacteristic(uuid(charId)) ?: throw IllegalStateException("Characteristic not found")
            if (!g.setCharacteristicNotification(c, enable)) throw IllegalStateException("Could not enable notifications")
            val d = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (d != null) {
                val value = if (!enable) BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE else if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, value) else {
                    @Suppress("DEPRECATION") d.value = value
                    @Suppress("DEPRECATION") g.writeDescriptor(d)
                }
            }
            resolve(id, JSONObject().put("ok", true))
        } catch (e: Exception) { resolve(id, JSONObject().put("error", e.message ?: "Notify failed")) }
    }

    @JavascriptInterface fun disconnect() { try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}; gatt = null; jsCall("disconnected", JSONObject()) }
    fun close() { disconnect(); try { scannerCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) } } catch (_: Exception) {} }
}
