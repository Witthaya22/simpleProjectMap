package com.example.simpleprojectmap

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    // Views
    private lateinit var mapView: MapView
    private lateinit var arrowView: ImageView
    private lateinit var distanceText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    // GPS
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // สถานะการนำทาง
    private var isNavigating = false
    private var currentWaypointIndex = 0
    private var userMarker: Marker? = null

    // Waypoints ตัวอย่าง 5 จุด บริเวณสยามสแควร์ กรุงเทพฯ
    // ห่างกันประมาณ 55 เมตร เป็นรูปตัว L
    private val waypoints = listOf(
        GeoPoint(13.744672, 100.530073), // จุดเริ่มต้น (แยกสยาม)
        GeoPoint(13.745117, 100.530073), // ~50 เมตรทางเหนือ
        GeoPoint(13.745117, 100.530640), // ~50 เมตรทางตะวันออก
        GeoPoint(13.745560, 100.530640), // ~50 เมตรทางเหนือ
        GeoPoint(13.745560, 100.531200)  // ~50 เมตรทางตะวันออก (จุดหมาย)
    )

    private val PERMISSION_CODE = 1001
    private val ARRIVE_DISTANCE = 15f // เมตร — ถือว่าถึงจุดเมื่อเข้าใกล้ 15 เมตร

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ตั้งค่า OSMDroid: user agent และ cache ไปที่ internal storage
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidTileCache = File(cacheDir, "osmdroid_tiles")
        }

        setContentView(R.layout.activity_main)

        // เชื่อมต่อ Views
        mapView = findViewById(R.id.mapView)
        arrowView = findViewById(R.id.arrowView)
        distanceText = findViewById(R.id.distanceText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        setupMap()
        drawRouteOnMap()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()

        startButton.setOnClickListener {
            if (hasLocationPermission()) {
                startNavigation()
            } else {
                askLocationPermission()
            }
        }

        stopButton.setOnClickListener {
            stopNavigation()
        }

        // ขอ permission ตั้งแต่เปิดแอป
        if (!hasLocationPermission()) {
            askLocationPermission()
        }
    }

    // ตั้งค่าแผนที่เริ่มต้น
    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)
        mapView.controller.setCenter(waypoints[0])
    }

    // วาดเส้นทางและ marker บนแผนที่
    private fun drawRouteOnMap() {
        // เส้นทางสีแดงเชื่อมทุก waypoint
        val route = Polyline(mapView)
        route.setPoints(waypoints)
        route.outlinePaint.color = Color.RED
        route.outlinePaint.strokeWidth = 10f
        mapView.overlays.add(route)

        // marker แต่ละจุด
        waypoints.forEachIndexed { index, point ->
            val marker = Marker(mapView)
            marker.position = point
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = when (index) {
                0 -> "จุดเริ่มต้น"
                waypoints.size - 1 -> "จุดหมายปลายทาง"
                else -> "จุดผ่านที่ $index"
            }
            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    // ตั้งค่า callback รับค่า GPS
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { processLocation(it) }
            }
        }
    }

    // เริ่มนำทาง — เปิด GPS tracking
    @SuppressLint("MissingPermission")
    private fun startNavigation() {
        isNavigating = true
        currentWaypointIndex = 0
        startButton.isEnabled = false
        stopButton.isEnabled = true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

        Toast.makeText(this, "เริ่มนำทางแล้ว — มุ่งหน้าไป: ${waypoints.size} จุด", Toast.LENGTH_SHORT).show()
        mapView.controller.setZoom(19.0)
    }

    // หยุดนำทาง — ปิด GPS tracking
    private fun stopNavigation() {
        isNavigating = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        startButton.isEnabled = true
        stopButton.isEnabled = false
        distanceText.text = "ระยะทางที่เหลือ: -- เมตร"
        arrowView.rotation = 0f
    }

    // ประมวลผลตำแหน่ง GPS ที่ได้รับ
    private fun processLocation(location: Location) {
        val userPoint = GeoPoint(location.latitude, location.longitude)

        // อัปเดตจุดสีน้ำเงินบนแผนที่
        refreshUserMarker(userPoint)

        if (!isNavigating || currentWaypointIndex >= waypoints.size) return

        val target = waypoints[currentWaypointIndex]
        val targetLocation = Location("target").also {
            it.latitude = target.latitude
            it.longitude = target.longitude
        }

        val distToTarget = location.distanceTo(targetLocation)

        // คำนวณมุมและหมุนลูกศร
        val bearing = computeBearing(
            location.latitude, location.longitude,
            target.latitude, target.longitude
        )
        arrowView.rotation = bearing.toFloat()

        // แสดงระยะทางรวมที่เหลือ
        val remaining = computeTotalRemaining(location)
        distanceText.text = "ระยะทางที่เหลือ: ${remaining.toInt()} เมตร"

        // เลื่อนแผนที่ตามตำแหน่งผู้ใช้
        mapView.controller.animateTo(userPoint)

        // ถ้าเข้าใกล้ waypoint ≤ 15 เมตร → ข้ามไปจุดถัดไป
        if (distToTarget <= ARRIVE_DISTANCE) {
            currentWaypointIndex++
            if (currentWaypointIndex >= waypoints.size) {
                // ถึงจุดหมายสุดท้ายแล้ว
                stopNavigation()
                showArrivalDialog()
            } else {
                val passedNum = currentWaypointIndex
                Toast.makeText(this, "ผ่านจุดที่ $passedNum แล้ว!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // อัปเดต marker ตำแหน่งผู้ใช้ (จุดสีน้ำเงิน)
    private fun refreshUserMarker(point: GeoPoint) {
        userMarker?.let { mapView.overlays.remove(it) }

        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_user_location)
        marker.title = "ตำแหน่งของคุณ"

        userMarker = marker
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    // คำนวณ bearing (มุมองศา 0–360) จากจุด A ไปยังจุด B
    // 0° = เหนือ, 90° = ตะวันออก, 180° = ใต้, 270° = ตะวันตก
    private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    // คำนวณระยะทางรวมที่เหลือ (ตำแหน่งปัจจุบัน → waypoints ที่เหลือทั้งหมด)
    private fun computeTotalRemaining(current: Location): Float {
        if (currentWaypointIndex >= waypoints.size) return 0f

        var total = 0f

        // ระยะจากตำแหน่งปัจจุบันถึง waypoint แรกที่ยังไม่ถึง
        val firstTarget = waypoints[currentWaypointIndex]
        total += current.distanceTo(Location("").also {
            it.latitude = firstTarget.latitude
            it.longitude = firstTarget.longitude
        })

        // รวมระยะระหว่าง waypoints ที่เหลือ
        for (i in currentWaypointIndex until waypoints.size - 1) {
            val from = Location("").also {
                it.latitude = waypoints[i].latitude
                it.longitude = waypoints[i].longitude
            }
            val to = Location("").also {
                it.latitude = waypoints[i + 1].latitude
                it.longitude = waypoints[i + 1].longitude
            }
            total += from.distanceTo(to)
        }

        return total
    }

    // Dialog แสดงเมื่อถึงจุดหมายสุดท้าย
    private fun showArrivalDialog() {
        AlertDialog.Builder(this)
            .setTitle("ถึงจุดหมายแล้ว!")
            .setMessage("คุณเดินทางถึงจุดหมายปลายทางเรียบร้อยแล้ว\nขอแสดงความยินดี!")
            .setPositiveButton("ตกลง") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    // ตรวจสอบว่ามี permission GPS หรือยัง
    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    // ขอ permission GPS
    private fun askLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_CODE
        )
    }

    // รับผลลัพธ์การขอ permission
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "ได้รับอนุญาตใช้ GPS แล้ว กดปุ่มเริ่มนำทาง", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ต้องอนุญาตใช้ GPS เพื่อนำทาง", Toast.LENGTH_LONG).show()
            }
        }
    }

    // lifecycle ของ OSMDroid — ต้องเรียก onResume/onPause/onDetach ด้วย
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isNavigating) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        mapView.onDetach()
    }
}
