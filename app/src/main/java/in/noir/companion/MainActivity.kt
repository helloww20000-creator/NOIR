package com.noir.companion

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
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
        listOf("Home","Device","Face Lab","Health","Settings").forEach { item ->
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
                found.forEach { (address,name) -> body.addView(card(name,address,"Advertisement discovered; not connected")) }
                body.addView(card("SAFETY","No unknown writes","NOIR does not send undocumented commands or flash watch firmware."))
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
                body.addView(card("EXPORT","Preview only","Exact NSW-309 face format and transfer protocol are not verified yet."))
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
    override fun onDestroy(){stopScan();super.onDestroy()}

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