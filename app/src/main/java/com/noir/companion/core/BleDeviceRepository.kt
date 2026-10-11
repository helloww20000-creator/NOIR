package com.noir.companion.core

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Conservative BLE transport for NOIR. Discovery and reads are safe defaults;
 * writes are intentionally restricted to characteristics explicitly selected by callers.
 */
class BleDeviceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()
    private val seen = linkedMapOf<String, Device>()
    private val logFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    data class Device(val name: String, val address: String, val rssi: Int)
    data class CharacteristicInfo(
        val serviceUuid: String,
        val uuid: String,
        val properties: List<String>,
        val valueHex: String? = null
    )
    data class Event(val timestamp: String, val category: String, val message: String) {
        override fun toString() = "[$timestamp] $category: $message"
    }

    fun addListener(listener: (Event) -> Unit) { listeners += listener }
    fun removeListener(listener: (Event) -> Unit) { listeners -= listener }
    fun devices(): List<Device> = synchronized(seen) { seen.values.toList() }
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true
    fun isConnected(): Boolean = gatt != null

    private fun emit(category: String, message: String) {
        val event = Event(logFormat.format(Date()), category, message)
        main.post { listeners.forEach { it(event) } }
    }

    fun hasScanPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasScanPermission()) { emit("permission", "BLUETOOTH_SCAN permission is required"); return }
        if (scanning) return
        val leScanner = scanner
        if (adapter?.isEnabled != true || leScanner == null) { emit("bluetooth", "Bluetooth is disabled or LE scanner unavailable"); return }
        synchronized(seen) { seen.clear() }
        scanning = true
        emit("scan", "Started BLE scan")
        try { leScanner.startScan(scanCallback) } catch (e: SecurityException) {
            scanning = false
            emit("error", "Scan permission denied: ${e.message}")
        } catch (e: IllegalStateException) {
            scanning = false
            emit("error", "Could not start scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) { }
        scanning = false
        emit("scan", "Stopped BLE scan")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val address = try { device.address } catch (_: SecurityException) { return }
            val name = result.scanRecord?.deviceName ?: try { device.name } catch (_: SecurityException) { null } ?: "Unknown device"
            synchronized(seen) { seen[address] = Device(name, address, result.rssi) }
            emit("device", "$name • RSSI ${result.rssi} dBm")
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            emit("error", "BLE scan failed with code $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!hasConnectPermission()) { emit("permission", "BLUETOOTH_CONNECT permission is required"); return }
        stopScan()
        val device = try { adapter?.getRemoteDevice(address) } catch (e: IllegalArgumentException) { null }
        if (device == null) { emit("error", "Invalid Bluetooth address"); return }
        gatt?.close()
        gatt = null
        emit("connect", "Connecting to $address")
        try {
            gatt = if (Build.VERSION.SDK_INT >= 23) device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                   else device.connectGatt(appContext, false, gattCallback)
        } catch (e: Exception) { emit("error", "Connection failed: ${e.message}") }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        try { gatt?.disconnect() } catch (_: Exception) { }
        try { gatt?.close() } catch (_: Exception) { }
        gatt = null
        emit("connect", "Disconnected")
    }

    @SuppressLint("MissingPermission")
    fun read(serviceUuid: UUID, characteristicUuid: UUID) {
        val characteristic = gatt?.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (characteristic == null) { emit("error", "Characteristic $characteristicUuid not found"); return }
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
            emit("error", "Characteristic $characteristicUuid is not readable"); return
        }
        if (gatt?.readCharacteristic(characteristic) != true) emit("error", "Read request was not accepted")
        else emit("gatt", "Read requested: $characteristicUuid")
    }

    @SuppressLint("MissingPermission")
    fun enableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        val currentGatt = gatt ?: return emit("error", "Not connected")
        val characteristic = currentGatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
        if (characteristic == null) { emit("error", "Characteristic $characteristicUuid not found"); return }
        val props = characteristic.properties
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0 && props and BluetoothGattCharacteristic.PROPERTY_INDICATE == 0) {
            emit("error", "Characteristic does not support notifications/indications"); return
        }
        if (!currentGatt.setCharacteristicNotification(characteristic, true)) {
            emit("error", "Could not enable local notification routing"); return
        }
        val cccd = characteristic.getDescriptor(CCCD_UUID)
        if (cccd == null) { emit("error", "CCCD descriptor not exposed by this characteristic"); return }
        cccd.value = if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        if (!currentGatt.writeDescriptor(cccd)) emit("error", "CCCD write was not accepted")
        else emit("gatt", "Notification subscription requested for $characteristicUuid")
    }

    @SuppressLint("MissingPermission")
    fun readAllReadableCharacteristics() {
        val services = gatt?.services ?: return emit("error", "Discover services before reading")
        val readable = services.flatMap { service -> service.characteristics.filter {
            it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
        }.map { service.uuid to it.uuid } }
        emit("gatt", "Found ${readable.size} readable characteristics; use individual reads to avoid overloading GATT")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            emit("connect", "GATT status=$status state=${if (newState == BluetoothProfile.STATE_CONNECTED) "connected" else if (newState == BluetoothProfile.STATE_DISCONNECTED) "disconnected" else newState}")
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (gatt === g) gatt = null
                try { g.close() } catch (_: Exception) { }
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!hasConnectPermission()) { emit("permission", "Connect permission missing; cannot discover services"); return }
                val accepted = g.discoverServices()
                emit("gatt", "Service discovery requested: $accepted")
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { emit("error", "Service discovery failed: $status"); return }
            g.services.forEach { service ->
                emit("service", service.uuid.toString())
                service.characteristics.forEach { c ->
                    val p = mutableListOf<String>()
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) p += "READ"
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) p += "WRITE"
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) p += "WRITE_NO_RESPONSE"
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) p += "NOTIFY"
                    if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) p += "INDICATE"
                    emit("characteristic", "${c.uuid} [${p.joinToString(",")}]")
                }
            }
        }
        @Deprecated("Deprecated on Android 13")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) emit("read", "${c.uuid} = ${c.value.toHex()}")
            else emit("error", "Read ${c.uuid} failed: $status")
        }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) emit("read", "${c.uuid} = ${value.toHex()}")
            else emit("error", "Read ${c.uuid} failed: $status")
        }
        @Deprecated("Deprecated on Android 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            emit("notify", "${c.uuid} = ${c.value.toHex()}")
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            emit("notify", "${c.uuid} = ${value.toHex()}")
        }
        @Deprecated("Deprecated on Android 13")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            emit(if (status == BluetoothGatt.GATT_SUCCESS) "gatt" else "error", "Descriptor ${descriptor.uuid} write status=$status")
        }
    }

    fun discoveredCharacteristics(): List<CharacteristicInfo> = gatt?.services?.flatMap { service ->
        service.characteristics.map { c ->
            val p = buildList {
                if (c.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
                if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                if (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
            }
            CharacteristicInfo(service.uuid.toString(), c.uuid.toString(), p)
        }
    } ?: emptyList()

    companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        fun ByteArray.toHex(): String = joinToString("-") { "%02X".format(it) }
    }
}
