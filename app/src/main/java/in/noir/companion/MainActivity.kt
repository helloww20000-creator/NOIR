package com.noir.companion

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.content.Intent
import android.net.Uri
import java.io.OutputStreamWriter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : Activity() {
    private val bg = Color.rgb(8,10,14)
    private val panel = Color.rgb(20,23,30)
    private val white = Color.rgb(240,243,248)
    private val muted = Color.rgb(145,154,169)
    private val accent = Color.rgb(127,190,255)
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var page = "Home"
    private val found = linkedMapOf<String,String>()
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var face: FaceView? = null
    private var gatt: BluetoothGatt? = null
    private var connectedAddress: String? = null
    private var connectionState = "Not connected"
    private var gattDetails = "Tap a device to connect and inspect services."
    private val diagnosticLines = mutableListOf<String>()
    private val readableQueue = java.util.ArrayDeque<BluetoothGattCharacteristic>()
    private val notifyQueue = java.util.ArrayDeque<BluetoothGattCharacteristic>()
    private var readingDiagnostics = false
    private var enablingNotifications = false
    private var pendingReport = ""
    private var activeDiagnosticGatt: BluetoothGatt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        setContentView(buildShell())
        requestPermissionsIfNeeded()
        render()
    }
    private fun buildShell(): View {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,18,20,8) }
        head.addView(txt("N O I R",12,accent,true))
        head.addView(txt("Your watch. Your rules.",24,white,true))
        head.addView(txt("NOISEFIT COMPANION • EARLY BUILD",10,muted,true))
        root.addView(head)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16,8,16,18) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(3,6,3,10); setBackgroundColor(Color.rgb(13,15,20)) }
        listOf("Home","Device","Face Lab","Health","Alerts","Settings").forEach { item ->
            nav.addView(TextView(this).apply {
                text=item; textSize=10f; gravity=Gravity.CENTER; setTextColor(if(page==item) accent else muted); setPadding(5,12,5,12)
                setOnClickListener { page=item; render() }
            }, LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(nav)
        return root
    }
    private fun requestPermissionsIfNeeded() {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_SCAN)
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS)
        if (p.isNotEmpty()) requestPermissions(p.toTypedArray(),7)
    }
    private fun render() {
        body.removeAllViews()
        when(page) {
            "Home" -> {
                body.addView(card("DEVICE TARGET","NoiseFit Twist Go Elite Edition 5","NSW-309 • firmware MOV-BOU2-2.1.1"))
                body.addView(txt("Your day at a glance",20,white,true))
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                row.addView(card("STEPS","—","Waiting for verified watch sync"),LinearLayout.LayoutParams(0,-2,1f))
                row.addView(card("HEART RATE","— bpm","Waiting for verified watch sync"),LinearLayout.LayoutParams(0,-2,1f))
                body.addView(row)
                val row2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                row2.addView(card("SLEEP","—","Waiting for verified watch sync"),LinearLayout.LayoutParams(0,-2,1f))
                row2.addView(card("BATTERY","—%","Waiting for verified watch sync"),LinearLayout.LayoutParams(0,-2,1f))
                body.addView(row2)
                body.addView(card("STATUS","NOIR foundation","Native UI and BLE discovery. Health sync, notifications and watch-face upload need exact-model protocol work."))
                action("Find your watch","Bluetooth LE discovery") { page="Device"; render(); scan() }
                action("Design a watch face","Open the local canvas editor") { page="Face Lab"; render() }
            }
            "Device" -> {
                body.addView(txt("Device Lab",24,white,true))
                body.addView(txt("Bluetooth discovery • read-only",12,muted))
                action(if(scanning) "Scanning…" else "Scan Bluetooth LE","Search nearby devices") { scan() }
                action("Stop scan","Stop Bluetooth discovery") { stopScan() }
                if(found.isEmpty()) body.addView(card("NO RESULTS","No devices discovered yet","Turn Bluetooth on, grant permission and keep the watch nearby."))
                found.forEach { (address,name) ->
                    val item = card("BLE DEVICE", name, address + if (address == connectedAddress) " • connected" else " • tap to connect")
                    item.setOnClickListener { connectToDevice(address) }
                    body.addView(item)
                }
                body.addView(card("CONNECTION", connectionState, gattDetails))
                if (gatt != null) {
                    action("Read characteristics", "Read only characteristics that explicitly allow READ") { readReadableCharacteristics() }
                    action("Monitor notifications", "Subscribe to supported NOTIFY characteristics") { startNotificationMonitor() }
                    action("Stop diagnostics", "Stop queued reads and notification subscriptions") { stopDiagnostics() }
                    action("Export diagnostic report", "Save UUIDs, properties, values, and notification samples") { exportDiagnosticReport() }
                    body.addView(card("DIAGNOSTICS", "${diagnosticLines.size} log entries", "Read and notification results stay on this phone until you export the report."))
                }
                if (gatt != null) action("Disconnect", "Close BLE connection") { disconnectGatt() }
                body.addView(card("SAFETY","Read-only inspection","No undocumented commands are sent."))
            }
            "Face Lab" -> {
                body.addView(txt("Face Studio",24,white,true))
                body.addView(txt("Live canvas preview • editor prototype",12,muted))
                face=FaceView(this)
                body.addView(face,LinearLayout.LayoutParams(-1,0,1f))
                val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                listOf("ANALOG","DIGITAL","HYBRID").forEachIndexed { i,s -> r.addView(btn(s){face?.mode=i;face?.invalidate()},LinearLayout.LayoutParams(0,-2,1f)) }
                body.addView(r)
                val r2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                listOf("METAL","CARBON","GOLD").forEachIndexed { i,s -> r2.addView(btn(s){face?.theme=i;face?.invalidate()},LinearLayout.LayoutParams(0,-2,1f)) }
                body.addView(r2)
                action("Toggle custom text","Show or hide preview label") { face?.customText=!(face?.customText ?: false); face?.invalidate() }
                action("Save face preset","Save the current preview design on this phone") {
                    val v = face
                    if (v != null) {
                        getSharedPreferences("noir_faces", MODE_PRIVATE).edit().putInt("mode", v.mode).putInt("theme", v.theme).putBoolean("customText", v.customText).apply()
                        toast("Face preset saved on this phone")
                    }
                }
                action("Load saved preset","Restore your last saved preview design") {
                    val prefs = getSharedPreferences("noir_faces", MODE_PRIVATE)
                    val v = face
                    if (v != null && prefs.contains("mode")) {
                        v.mode = prefs.getInt("mode", 2); v.theme = prefs.getInt("theme", 0); v.customText = prefs.getBoolean("customText", false); v.invalidate()
                        toast("Saved preset loaded")
                    } else toast("No saved preset yet")
                }
                body.addView(card("EXPORT","Local preset only","Preset saving works locally. Device-compatible face packaging and transfer require reverse-engineering the NSW-309 protocol."))
            }
            "Alerts" -> {
                body.addView(txt("Notification bridge",24,white,true))
                body.addView(txt("Phone notifications • optional permission",12,muted))
                action("Enable notification access","Open Android system settings") {
                    try { startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } catch (_: Exception) { toast("Open Android Settings and search Notification access") }
                }
                action("Clear mirrored notifications","Delete locally stored notification previews") { getSharedPreferences("noir_notifications", MODE_PRIVATE).edit().remove("items").apply(); render() }
                val arr = try { org.json.JSONArray(getSharedPreferences("noir_notifications", MODE_PRIVATE).getString("items", "[]")) } catch (_: Exception) { org.json.JSONArray() }
                if (arr.length() == 0) body.addView(card("INBOX","No notifications captured","Enable NOIR under Android Notification access. Watch delivery still needs protocol support."))
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    body.addView(card(item.optString("package", "App"), item.optString("title", "Notification"), item.optString("text", "")))
                }
                body.addView(card("WATCH DELIVERY","Not yet connected","Phone notification capture works locally. Forwarding to NSW-309 requires a verified BLE command protocol."))
            }
            "Health" -> {
                body.addView(txt("Health & activity",24,white,true))
                body.addView(txt("Real readings only — no fabricated data.",12,muted))
                listOf("Steps and distance","Calories","Heart rate","SpO₂","Sleep stages","Stress","Workout history","Goals and reminders").forEach { body.addView(card(it,"Not connected","Requires verified sensor data synchronisation.")) }
            }
            else -> {
                body.addView(txt("Settings",24,white,true))
                body.addView(card("ABOUT","NOIR 0.1.0","Native Android companion prototype for NSW-309."))
                body.addView(card("PRIVACY","Local-first","No account or analytics backend is included."))
                action("Request permissions","Bluetooth and notification permissions") { requestPermissionsIfNeeded() }
                action("Clear scan results","Remove temporary discovered devices") { found.clear(); render() }
                body.addView(card("NEXT","GATT inspector","Inspect service UUIDs and characteristics before implementing commands."))
            }
        }
    }
    private fun scan() {
        val manager=getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter=manager.adapter
        if(adapter==null || !adapter.isEnabled) { toast("Turn Bluetooth on first"); return }
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED) { requestPermissionsIfNeeded(); toast("Grant nearby devices permission"); return }
        try {
            stopScan(); found.clear(); scanning=true; render()
            adapter.bluetoothLeScanner.startScan(null,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback)
            toast("Scanning for 12 seconds")
            handler.postDelayed({stopScan();if(page=="Device")render()},12000)
        } catch(e:Exception) { scanning=false;toast("Scan failed: ${e.message}") }
    }
    private val callback=object:ScanCallback(){
        override fun onScanResult(type:Int,result:ScanResult) {
            runOnUiThread {
                val address=try{result.device.address}catch(_:Exception){"Unknown"}
                val name=try{result.scanRecord?.deviceName ?: result.device.name ?: "Unnamed BLE device"}catch(_:Exception){"BLE device"}
                found[address]=name
                if(page=="Device") render()
            }
        }
        override fun onScanFailed(code:Int){runOnUiThread{scanning=false;toast("BLE scan failed: $code");render()}}
    }
    private fun connectToDevice(address: String) {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { requestPermissionsIfNeeded(); toast("Grant Nearby devices permission and retry"); return }
        try {
            stopScan(); try { gatt?.close() } catch (_: Exception) {}; gatt = null
            connectedAddress = address; connectionState = "Connecting…"; gattDetails = "Waiting for device response."; render()
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val device = adapter.getRemoteDevice(address)
            gatt = if (Build.VERSION.SDK_INT >= 23) device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE) else device.connectGatt(this, false, gattCallback)
        } catch (e: Exception) { connectionState = "Connection failed"; gattDetails = e.message ?: "Bluetooth error"; render() }
    }
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                if (status != BluetoothGatt.GATT_SUCCESS) { connectionState = "Connection error: " + status; gattDetails = "Connection rejected or dropped."; g.close(); if (gatt === g) gatt = null; render(); return@runOnUiThread }
                if (newState == BluetoothProfile.STATE_CONNECTED) { connectionState = "Connected; discovering services…"; render(); if (!g.discoverServices()) { connectionState = "Service discovery failed to start"; render() } }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) { connectionState = "Disconnected"; if (gatt === g) { g.close(); gatt = null }; render() }
            }
        }
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val value = if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null
            val hex = value?.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } ?: "(read failed: status $status)"
            runOnUiThread {
                if (gatt !== g) return@runOnUiThread
                diagnosticLines.add("READ ${characteristic.uuid}: $hex")
                if (readingDiagnostics) readNextCharacteristic(g)
            }
        }
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: byteArrayOf()
            val hex = value.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            runOnUiThread {
                if (gatt !== g) return@runOnUiThread
                val entry = "NOTIFY ${characteristic.uuid}: $hex"
                diagnosticLines.add(entry)
                if (diagnosticLines.size > 2000) diagnosticLines.removeAt(0)
                gattDetails = "Notification captured • ${characteristic.uuid} • $hex"
                if (page == "Device") render()
            }
        }
        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            runOnUiThread {
                if (gatt !== g) return@runOnUiThread
                diagnosticLines.add("CCCD ${descriptor.characteristic.uuid}: status $status")
                if (enablingNotifications) enableNextNotification(g)
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            runOnUiThread {
                if (status != BluetoothGatt.GATT_SUCCESS) { gattDetails = "Service discovery failed: " + status }
                else {
                    val lines = mutableListOf<String>()
                    diagnosticLines.clear()
                    diagnosticLines.add("NOIR BLE diagnostic report")
                    diagnosticLines.add("Device address: ${connectedAddress ?: "unknown"}")
                    diagnosticLines.add("Captured: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
                    diagnosticLines.add("Service count: ${g.services.size}")
                    for (service in g.services) {
                        lines.add("SERVICE " + service.uuid.toString())
                        diagnosticLines.add("SERVICE " + service.uuid.toString())
                        for (ch in service.characteristics) {
                            val p = ch.properties; val flags = mutableListOf<String>()
                            if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) flags.add("READ")
                            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) flags.add("WRITE")
                            if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) flags.add("WRITE_NO_RESPONSE")
                            if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) flags.add("NOTIFY")
                            if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) flags.add("INDICATE")
                            lines.add("  " + ch.uuid.toString() + " [" + flags.joinToString(", ") + "]")
                            diagnosticLines.add("  " + ch.uuid.toString() + " [" + flags.joinToString(", ") + "]")
                        }
                    }
                    gattDetails = if (lines.isEmpty()) "No services reported" else lines.joinToString("\\n")
                    connectionState = "Connected • " + g.services.size + " services found"
                }
                render()
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun readReadableCharacteristics() {
        val current = gatt
        if (current == null || connectionState.startsWith("Not connected") || connectionState == "Disconnected") {
            toast("Connect to the watch first"); return
        }
        readableQueue.clear()
        for (service in current.services) for (ch in service.characteristics) {
            if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) readableQueue.add(ch)
        }
        if (readableQueue.isEmpty()) { toast("No readable characteristics found"); return }
        readingDiagnostics = true
        diagnosticLines.add("Starting read-only inspection: ${readableQueue.size} readable characteristics")
        readNextCharacteristic(current)
        toast("Reading characteristics safely")
    }

    @Suppress("DEPRECATION")
    private fun readNextCharacteristic(current: BluetoothGatt) {
        if (!readingDiagnostics || gatt !== current) return
        val next = if (readableQueue.isEmpty()) null else readableQueue.removeFirst()
        if (next == null) {
            readingDiagnostics = false
            diagnosticLines.add("Read-only inspection complete")
            gattDetails = "Read-only inspection complete • ${diagnosticLines.size} log entries"
            if (page == "Device") render()
            return
        }
        val started = try { current.readCharacteristic(next) } catch (_: Exception) { false }
        if (!started) {
            diagnosticLines.add("READ ${next.uuid}: request could not be started")
            handler.postDelayed({ readNextCharacteristic(current) }, 250)
        }
    }

    @Suppress("DEPRECATION")
    private fun startNotificationMonitor() {
        val current = gatt
        if (current == null) { toast("Connect to the watch first"); return }
        notifyQueue.clear()
        for (service in current.services) for (ch in service.characteristics) {
            if (ch.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 &&
                ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) != null) {
                notifyQueue.add(ch)
            }
        }
        if (notifyQueue.isEmpty()) { toast("No notification characteristics with CCCD descriptors found"); return }
        enablingNotifications = true
        diagnosticLines.add("Starting notification subscriptions for ${notifyQueue.size} characteristics")
        enableNextNotification(current)
        toast("Enabling supported notifications")
    }

    @Suppress("DEPRECATION")
    private fun enableNextNotification(current: BluetoothGatt) {
        if (!enablingNotifications || gatt !== current) return
        val ch = if (notifyQueue.isEmpty()) null else notifyQueue.removeFirst()
        if (ch == null) {
            enablingNotifications = false
            diagnosticLines.add("Notification subscription setup complete; waiting for device data")
            gattDetails = "Notification monitoring active"
            if (page == "Device") render()
            return
        }
        try {
            if (!current.setCharacteristicNotification(ch, true)) {
                diagnosticLines.add("NOTIFY ${ch.uuid}: Android subscription rejected")
                handler.postDelayed({ enableNextNotification(current) }, 150)
                return
            }
            val descriptor = ch.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor == null) {
                diagnosticLines.add("NOTIFY ${ch.uuid}: CCCD descriptor missing")
                handler.postDelayed({ enableNextNotification(current) }, 150)
                return
            }
            descriptor.value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            if (!current.writeDescriptor(descriptor)) {
                diagnosticLines.add("CCCD ${ch.uuid}: descriptor write could not be started")
                handler.postDelayed({ enableNextNotification(current) }, 150)
            }
        } catch (e: Exception) {
            diagnosticLines.add("NOTIFY ${ch.uuid}: ${e.message ?: "setup error"}")
            handler.postDelayed({ enableNextNotification(current) }, 150)
        }
    }

    private fun stopDiagnostics() {
        readingDiagnostics = false
        enablingNotifications = false
        readableQueue.clear()
        notifyQueue.clear()
        diagnosticLines.add("Diagnostics stopped by user")
        toast("Queued diagnostics stopped")
        if (page == "Device") render()
    }

    private fun exportDiagnosticReport() {
        val report = buildString {
            appendLine("NOIR NSW-309 BLE Diagnostic Report")
            appendLine("Generated: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date()))
            appendLine("Device address: ${connectedAddress ?: "unknown"}")
            appendLine("Connection: $connectionState")
            appendLine("Services:")
            appendLine(gattDetails.replace("\\n", "\n"))
            appendLine()
            diagnosticLines.forEach { appendLine(it) }
        }
        pendingReport = report
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "NOIR-NSW309-diagnostics.txt")
        }
        try { startActivityForResult(intent, 814) } catch (e: Exception) { toast("Could not open file saver: ${e.message}") }
    }

    @Deprecated("Uses document picker result callback for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 814 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(pendingReport) }
                }
                toast("Diagnostic report exported")
            } catch (e: Exception) { toast("Export failed: ${e.message}") }
        }
    }

    private fun disconnectGatt() { stopDiagnostics(); try { gatt?.disconnect() } catch (_: Exception) {}; try { gatt?.close() } catch (_: Exception) {}; gatt = null; connectedAddress = null; connectionState = "Not connected"; gattDetails = "Tap a device to connect and inspect services."; render() }

    private fun stopScan() {
        try {
            if(Build.VERSION.SDK_INT<31 || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED)
                (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch(_:Exception) {}
        scanning=false
    }
    private fun txt(s:String,size:Int,color:Int,bold:Boolean=false)=TextView(this).apply{
        text=s;textSize=size.toFloat();setTextColor(color);setPadding(3,5,3,5)
        if(bold)setTypeface(typeface,android.graphics.Typeface.BOLD)
    }
    private fun card(k:String,title:String,desc:String)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding(13,10,13,12)
        background=android.graphics.drawable.GradientDrawable().apply{setColor(panel);cornerRadius=20f}
        addView(txt(k,9,accent,true));addView(txt(title,15,white,true));addView(txt(desc,12,muted))
        val lp=LinearLayout.LayoutParams(-1,-2);lp.setMargins(3,5,3,7);layoutParams=lp
    }
    private fun action(title:String,desc:String,run:()->Unit) {
        val c=card("ACTION",title,desc); c.setOnClickListener{run()};body.addView(c)
    }
    private fun btn(s:String,run:()->Unit)=TextView(this).apply{
        text=s;textSize=10f;gravity=Gravity.CENTER;setTextColor(accent);setPadding(4,13,4,13)
        background=android.graphics.drawable.GradientDrawable().apply{setColor(panel);cornerRadius=13f}
        setOnClickListener{run()}
        layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(3,5,3,5)}
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    override fun onDestroy(){stopScan();try{gatt?.disconnect();gatt?.close()}catch(_:Exception){};gatt=null;super.onDestroy()}

    inner class FaceView(ctx:Context):View(ctx) {
        var mode=2
        var theme=0
        var customText=false
        private val p=Paint(3)
        private val clockTick = object : Runnable {
            override fun run() {
                if (isAttachedToWindow) {
                    invalidate()
                    handler.postDelayed(this, 1000)
                }
            }
        }
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            handler.removeCallbacks(clockTick)
            handler.post(clockTick)
        }
        override fun onDetachedFromWindow() {
            handler.removeCallbacks(clockTick)
            super.onDetachedFromWindow()
        }
        override fun onDraw(c:Canvas) {
            super.onDraw(c)
            c.drawColor(bg)
            val cx=width/2f;val cy=height/2f;val radius=minOf(width,height)*.40f
            val metal=when(theme){1->Color.rgb(75,83,91);2->Color.rgb(185,139,62);else->Color.rgb(153,166,180)}
            p.style=Paint.Style.FILL;p.color=Color.rgb(12,15,20);c.drawCircle(cx,cy,radius+8,p)
            p.style=Paint.Style.STROKE;p.strokeWidth=8f;p.color=metal;c.drawCircle(cx,cy,radius,p)
            p.strokeWidth=1.5f;p.color=accent;c.drawCircle(cx,cy,radius-12,p)
            p.style=Paint.Style.FILL;p.color=if(theme==1)Color.rgb(25,29,34) else Color.rgb(14,18,25);c.drawCircle(cx,cy,radius-18,p)
            p.color=metal;p.strokeWidth=2f
            for(i in 0 until 120){
                val a=Math.PI*2*i/120-Math.PI/2
                val r1=radius-if(i%5==0)27 else 22
                c.drawLine(cx+(cos(a)*r1).toFloat(),cy+(sin(a)*r1).toFloat(),cx+(cos(a)*(radius-13)).toFloat(),cy+(sin(a)*(radius-13)).toFloat(),p)
            }
            p.textAlign=Paint.Align.CENTER;p.color=white;p.textSize=radius*.13f
            if(mode!=1)for(i in 1..12){
                val a=Math.PI*2*i/12-Math.PI/2
                c.drawText(i.toString(),cx+(cos(a)*radius*.68).toFloat(),cy+(sin(a)*radius*.68).toFloat()+p.textSize*.35f,p)
            }
            p.style=Paint.Style.STROKE;p.strokeWidth=2f;p.color=metal
            c.drawCircle(cx,cy+radius*.30f,radius*.15f,p);c.drawCircle(cx-radius*.34f,cy+radius*.03f,radius*.12f,p);c.drawCircle(cx+radius*.34f,cy+radius*.03f,radius*.12f,p)
            p.style=Paint.Style.FILL;p.color=accent;p.textSize=radius*.07f;c.drawText("NOIR • FIELD SYSTEM",cx,cy-radius*.28f,p)
            val time=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
            if(mode!=0){p.color=white;p.textSize=radius*.26f;c.drawText(time,cx,cy+radius*.11f,p)}
            if(mode!=1){
                val cal=java.util.Calendar.getInstance()
                hand(c,cx,cy,radius*.34f,cal.get(java.util.Calendar.HOUR)*30.0+cal.get(java.util.Calendar.MINUTE)*.5,5f,white)
                hand(c,cx,cy,radius*.51f,cal.get(java.util.Calendar.MINUTE)*6.0,3f,white)
                hand(c,cx,cy,radius*.59f,cal.get(java.util.Calendar.SECOND)*6.0,1.5f,accent)
                p.style=Paint.Style.FILL;p.color=metal;c.drawCircle(cx,cy,6f,p)
            }
            p.color=muted;p.textSize=radius*.06f;c.drawText("WR • 05 ATM",cx,cy+radius*.72f,p)
            if(customText){p.color=Color.YELLOW;p.textSize=radius*.08f;c.drawText("CUSTOM TEXT",cx,cy+radius*.48f,p)}
        }
        private fun hand(c:Canvas,x:Float,y:Float,len:Float,degrees:Double,stroke:Float,color:Int){
            val a=Math.toRadians(degrees-90);p.color=color;p.strokeWidth=stroke;p.strokeCap=Paint.Cap.ROUND;p.style=Paint.Style.STROKE
            c.drawLine(x,y,x+(cos(a)*len).toFloat(),y+(sin(a)*len).toFloat(),p);p.style=Paint.Style.FILL
        }
    }
}