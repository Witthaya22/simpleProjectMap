package com.example.simpleprojectmap

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.MapTileIndex
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

class MainActivity : ComponentActivity() {

    // ── Locate Me ──
    private lateinit var locateMeButton: Button
    private var lastUserLocation: GeoPoint? = null

    // ── Pick Mode Views ──
    private lateinit var pickPanel: LinearLayout
    private lateinit var tapHintBanner: TextView
    private lateinit var waypointCountText: TextView
    private lateinit var undoButton: Button
    private lateinit var clearButton: Button
    private lateinit var startNavButton: Button
    private lateinit var downloadMapButton: Button

    // ── Nav Mode Views ──
    private lateinit var navPanel: LinearLayout
    private lateinit var targetLabel: TextView
    private lateinit var arrowView: ImageView
    private lateinit var distanceText: TextView
    private lateinit var stopButton: Button

    // ── Map ──
    private lateinit var mapView: MapView
    private lateinit var mapEventsOverlay: MapEventsOverlay

    // ── GPS ──
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // ── Navigation state ──
    private var isNavigating = false
    private var currentWaypointIndex = 0
    private var userMarker: Marker? = null
    private var routePolyline: Polyline? = null

    // ── Waypoints ที่ผู้ใช้แตะเลือก ──
    private val customWaypoints = mutableListOf<GeoPoint>()
    private val waypointMarkers = mutableListOf<Marker>()

    // จุดตั้งต้นกล้อง — ประตูหลัก มจพ. ปราจีนบุรี
    private val KMUTNB_GATE = GeoPoint(14.16100, 101.34720)

    private val PERMISSION_CODE = 1001
    private val ARRIVE_DISTANCE_M = 15f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // load() ก่อน setContentView — ให้ MapView ใน XML ได้ context ที่ถูกต้อง
        Configuration.getInstance().load(applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = File(filesDir, "osmdroid_tiles")
            tileDownloadThreads = 4              // โหลด tiles พร้อมกัน 4 thread = เร็วขึ้น
            tileDownloadMaxQueueSize = 40        // queue เยอะขึ้น
        }

        setContentView(R.layout.activity_main)
        bindViews()
        setupMap()
        setupGps()
        setupButtons()

        if (!hasGpsPermission()) askGpsPermission()
    }

    // ───────────────────────── Setup ─────────────────────────

    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        pickPanel = findViewById(R.id.pickPanel)
        tapHintBanner = findViewById(R.id.tapHintBanner)
        waypointCountText = findViewById(R.id.waypointCountText)
        undoButton = findViewById(R.id.undoButton)
        clearButton = findViewById(R.id.clearButton)
        startNavButton = findViewById(R.id.startNavButton)
        downloadMapButton = findViewById(R.id.downloadMapButton)
        navPanel = findViewById(R.id.navPanel)
        targetLabel = findViewById(R.id.targetLabel)
        arrowView = findViewById(R.id.arrowView)
        distanceText = findViewById(R.id.distanceText)
        stopButton = findViewById(R.id.stopButton)
        locateMeButton = findViewById(R.id.locateMeButton)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = false      // tiles คม ไม่ blur บน high-DPI
        mapView.setUseDataConnection(true)       // โหลด tiles จากอินเทอร์เน็ตได้เสมอ
        mapView.controller.setZoom(17.0)         // zoom 17 = มองเห็นพื้นที่ดีกว่า ก่อนซูมเข้าเอง
        mapView.controller.setCenter(KMUTNB_GATE)

        // MapEventsOverlay รับ tap บนแผนที่ — ต้องอยู่ท้ายสุดเสมอ
        // เพื่อให้ Markers ที่อยู่ก่อนหน้ามันรับ tap ก่อน (ถ้า tap ตรง marker → info window, ไม่เพิ่มจุด)
        mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!isNavigating) { addWaypoint(p); return true }
                return false
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mapView.overlays.add(mapEventsOverlay)
    }

    private fun setupGps() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { processLocation(it) }
            }
        }
    }

    private fun setupButtons() {
        undoButton.setOnClickListener { removeLastWaypoint() }

        clearButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("ล้างเส้นทาง")
                .setMessage("ต้องการล้างจุดทั้งหมดและเริ่มใหม่?")
                .setPositiveButton("ล้าง") { _, _ -> clearAllWaypoints() }
                .setNegativeButton("ยกเลิก", null)
                .show()
        }

        startNavButton.setOnClickListener {
            if (customWaypoints.size < 2) {
                Toast.makeText(this, "ต้องเพิ่มอย่างน้อย 2 จุดก่อน", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (hasGpsPermission()) startNavigation() else askGpsPermission()
        }

        stopButton.setOnClickListener { stopNavigation() }
        downloadMapButton.setOnClickListener { confirmOfflineDownload() }
        locateMeButton.setOnClickListener {
            val loc = lastUserLocation
            if (loc != null) mapView.controller.animateTo(loc)
            else Toast.makeText(this, "ยังไม่ได้รับสัญญาณ GPS", Toast.LENGTH_SHORT).show()
        }
    }

    // ───────────────────────── Waypoint Picking ─────────────────────────

    private fun addWaypoint(point: GeoPoint) {
        customWaypoints.add(point)
        val index = customWaypoints.size

        val marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "จุดที่ $index" + if (index == 1) " (เริ่มต้น)" else ""
        }

        // แทรก marker ก่อน MapEventsOverlay (เพื่อให้ marker รับ tap ได้)
        insertBeforeEventsOverlay(marker)
        waypointMarkers.add(marker)

        refreshRouteLine()
        updatePickUI()
        mapView.invalidate()
    }

    private fun removeLastWaypoint() {
        if (customWaypoints.isEmpty()) return
        customWaypoints.removeLast()
        val removed = waypointMarkers.removeLast()
        mapView.overlays.remove(removed)
        refreshRouteLine()
        updatePickUI()
        mapView.invalidate()
    }

    private fun clearAllWaypoints() {
        customWaypoints.clear()
        waypointMarkers.forEach { mapView.overlays.remove(it) }
        waypointMarkers.clear()
        routePolyline?.let { mapView.overlays.remove(it) }
        routePolyline = null
        updatePickUI()
        mapView.invalidate()
    }

    // วาดเส้นเชื่อมระหว่าง waypoints
    private fun refreshRouteLine() {
        routePolyline?.let { mapView.overlays.remove(it) }
        routePolyline = null
        if (customWaypoints.size < 2) return

        val line = Polyline(mapView).apply {
            setPoints(customWaypoints)
            outlinePaint.color = Color.RED
            outlinePaint.strokeWidth = 10f
        }
        insertBeforeEventsOverlay(line)
        routePolyline = line
    }

    // แทรก overlay ก่อน MapEventsOverlay เพื่อให้ events overlay อยู่ท้ายสุดเสมอ
    private fun insertBeforeEventsOverlay(overlay: org.osmdroid.views.overlay.Overlay) {
        val idx = mapView.overlays.indexOf(mapEventsOverlay)
        if (idx >= 0) mapView.overlays.add(idx, overlay)
        else mapView.overlays.add(overlay)
    }

    private fun updatePickUI() {
        val count = customWaypoints.size
        waypointCountText.text = when (count) {
            0 -> "แตะแผนที่เพื่อเพิ่มจุด (ยังไม่มีจุด)"
            1 -> "เพิ่มแล้ว 1 จุด — ต้องการอีกอย่างน้อย 1 จุด"
            else -> "เพิ่มแล้ว $count จุด — พร้อมเริ่มนำทาง"
        }
        undoButton.isEnabled = count > 0
        clearButton.isEnabled = count > 0
        startNavButton.isEnabled = count >= 2
    }

    // ───────────────────────── Navigation ─────────────────────────

    private fun startNavigation() {
        isNavigating = true
        currentWaypointIndex = 0

        // อัปเดต label ของจุดสุดท้ายให้ชัดเจน
        waypointMarkers.lastOrNull()?.title = "จุดที่ ${customWaypoints.size} (จุดหมาย)"

        // สลับ UI → nav mode
        pickPanel.visibility = View.GONE
        tapHintBanner.visibility = View.GONE
        navPanel.visibility = View.VISIBLE

        updateTargetLabel()

        Toast.makeText(this, "นำทาง ${customWaypoints.size} จุด — ออกเดินได้เลย!", Toast.LENGTH_SHORT).show()
        mapView.controller.setZoom(19.0)
    }

    private fun stopNavigation() {
        isNavigating = false

        // สลับ UI → pick mode
        navPanel.visibility = View.GONE
        pickPanel.visibility = View.VISIBLE
        tapHintBanner.visibility = View.VISIBLE

        distanceText.text = "ระยะทางที่เหลือ: -- เมตร"
        arrowView.rotation = 0f

        // ลบ marker ตำแหน่งผู้ใช้
        userMarker?.let { mapView.overlays.remove(it); userMarker = null }
        mapView.invalidate()
    }

    private fun processLocation(location: Location) {
        val userPoint = GeoPoint(location.latitude, location.longitude)
        lastUserLocation = userPoint
        refreshUserMarker(userPoint)

        if (!isNavigating || currentWaypointIndex >= customWaypoints.size) return

        val target = customWaypoints[currentWaypointIndex]
        val targetLoc = Location("target").also {
            it.latitude = target.latitude
            it.longitude = target.longitude
        }

        val distToTarget = location.distanceTo(targetLoc)

        // หมุนลูกศร
        val bearing = computeBearing(
            location.latitude, location.longitude,
            target.latitude, target.longitude
        )
        arrowView.rotation = bearing.toFloat()

        // ระยะทางรวมที่เหลือ
        val remaining = computeTotalRemaining(location)
        distanceText.text = "ระยะทางที่เหลือ: ${remaining.toInt()} เมตร"

        mapView.controller.animateTo(userPoint)

        // ถึงจุดปัจจุบัน (≤ 15 เมตร)
        if (distToTarget <= ARRIVE_DISTANCE_M) {
            currentWaypointIndex++
            if (currentWaypointIndex >= customWaypoints.size) {
                stopNavigation()
                showArrivalDialog()
            } else {
                Toast.makeText(this, "ผ่านจุดที่ $currentWaypointIndex แล้ว!", Toast.LENGTH_SHORT).show()
                updateTargetLabel()
            }
        }
    }

    // อัปเดตข้อความ "มุ่งหน้า: จุดที่ X"
    private fun updateTargetLabel() {
        val next = currentWaypointIndex + 1
        val isLast = next == customWaypoints.size
        targetLabel.text = "มุ่งหน้า: จุดที่ $next" + if (isLast) " (จุดหมาย)" else ""
    }

    // อัปเดต marker สีน้ำเงิน (ตำแหน่งผู้ใช้)
    private fun refreshUserMarker(point: GeoPoint) {
        userMarker?.let { mapView.overlays.remove(it) }
        val marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_user_location)
            title = "ตำแหน่งของคุณ"
        }
        insertBeforeEventsOverlay(marker)
        userMarker = marker
        mapView.invalidate()
    }

    // ───────────────────────── Calculations ─────────────────────────

    // Haversine bearing: องศา 0–360 (0=เหนือ, 90=ตะวันออก)
    private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    // ระยะทางรวมที่เหลือ: ตำแหน่งปัจจุบัน → จุดถัดไป → .... → จุดสุดท้าย
    private fun computeTotalRemaining(current: Location): Float {
        if (currentWaypointIndex >= customWaypoints.size) return 0f
        var total = current.distanceTo(Location("").also {
            it.latitude = customWaypoints[currentWaypointIndex].latitude
            it.longitude = customWaypoints[currentWaypointIndex].longitude
        })
        for (i in currentWaypointIndex until customWaypoints.size - 1) {
            total += Location("").also {
                it.latitude = customWaypoints[i].latitude
                it.longitude = customWaypoints[i].longitude
            }.distanceTo(Location("").also {
                it.latitude = customWaypoints[i + 1].latitude
                it.longitude = customWaypoints[i + 1].longitude
            })
        }
        return total
    }

    // ───────────────────────── Dialog & Permission ─────────────────────────

    private fun showArrivalDialog() {
        AlertDialog.Builder(this)
            .setTitle("ถึงจุดหมายแล้ว!")
            .setMessage("คุณเดินทางถึงจุดหมายปลายทางเรียบร้อยแล้ว!")
            .setPositiveButton("ตกลง") { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun hasGpsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun askGpsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "ได้รับอนุญาตใช้ GPS แล้ว", Toast.LENGTH_SHORT).show()
            startLocationUpdates()
        }
    }

    // ───────────────────────── Offline Tile Download ─────────────────────────

    private val ZOOM_MIN = 14
    private val ZOOM_MAX = 19

    // คำนวณพื้นที่ download: ถ้ามี waypoints ใช้ bbox ของ waypoints, ไม่งั้นใช้ viewport ปัจจุบัน
    private fun downloadBounds(): DoubleArray {
        return if (customWaypoints.size >= 2) {
            val buf = 0.005 // ~500m buffer รอบ waypoints
            doubleArrayOf(
                customWaypoints.minOf { it.latitude } - buf,
                customWaypoints.maxOf { it.latitude } + buf,
                customWaypoints.minOf { it.longitude } - buf,
                customWaypoints.maxOf { it.longitude } + buf
            )
        } else {
            val box = mapView.boundingBox
            val buf = 0.002
            doubleArrayOf(box.latSouth - buf, box.latNorth + buf, box.lonWest - buf, box.lonEast + buf)
        }
    }

    private fun confirmOfflineDownload() {
        if (!isNetworkAvailable()) {
            AlertDialog.Builder(this)
                .setTitle("ไม่มีอินเทอร์เน็ต")
                .setMessage("ต้องเชื่อมต่ออินเทอร์เน็ตก่อนดาวน์โหลดแผนที่\nหลังจากดาวน์โหลดแล้วจะใช้งาน offline ได้")
                .setPositiveButton("ตกลง", null)
                .show()
            return
        }

        val bounds = downloadBounds()
        val (south, north, west, east) = listOf(bounds[0], bounds[1], bounds[2], bounds[3])
        val areaDesc = if (customWaypoints.size >= 2)
            "พื้นที่ครอบ waypoints ของคุณ (${customWaypoints.size} จุด)"
        else
            "พื้นที่ที่กำลังดูอยู่บน map"

        val totalTiles = countTiles(south, north, west, east, ZOOM_MIN, ZOOM_MAX)
        val estimatedMB = totalTiles * 15 / 1024

        AlertDialog.Builder(this)
            .setTitle("ดาวน์โหลดแผนที่ offline")
            .setMessage(
                "พื้นที่: $areaDesc\n" +
                "Zoom: $ZOOM_MIN (ภาพรวม) → $ZOOM_MAX (รายละเอียด)\n" +
                "จำนวน tiles: ~$totalTiles (~${estimatedMB} MB)\n\n" +
                "ใช้ได้ทุกที่บน map — ไม่จำกัดแค่ มจพ."
            )
            .setPositiveButton("ดาวน์โหลด") { _, _ -> startOfflineDownload(south, north, west, east) }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun startOfflineDownload(south: Double, north: Double, west: Double, east: Double) {
        val tiles = buildTileList(south, north, west, east, ZOOM_MIN, ZOOM_MAX)
        val tileSource = mapView.tileProvider.tileSource as? OnlineTileSourceBase ?: run {
            Toast.makeText(this, "ไม่รองรับการดาวน์โหลด tile source นี้", Toast.LENGTH_SHORT).show()
            return
        }
        val tileWriter = mapView.tileProvider.tileWriter

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = tiles.size
        }
        val zoomText = TextView(this).apply {
            setPadding(0, 16, 0, 0)
            textSize = 13f
            text = "กำลังเริ่ม..."
        }
        val countText = TextView(this).apply {
            setPadding(0, 4, 0, 0)
            textSize = 11f
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(progressBar)
            addView(zoomText)
            addView(countText)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("กำลังดาวน์โหลดแผนที่ทั้ง campus...")
            .setView(layout)
            .setCancelable(false)
            .create()
        dialog.show()
        downloadMapButton.isEnabled = false

        Thread {
            var downloaded = 0
            var skipped = 0
            var failed = 0

            tiles.forEachIndexed { i, tileIndex ->
                val zoom = MapTileIndex.getZoom(tileIndex)
                try {
                    if (tileWriter != null && tileWriter.exists(tileSource, tileIndex)) {
                        skipped++
                    } else {
                        val url = tileSource.getTileURLString(tileIndex)
                        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("User-Agent", packageName)
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        conn.connect()
                        if (conn.responseCode == 200) {
                            tileWriter?.saveFile(tileSource, tileIndex, conn.inputStream, null)
                            downloaded++
                        } else {
                            failed++
                        }
                        conn.disconnect()
                        Thread.sleep(80) // polite rate limit — OSM policy
                    }
                } catch (_: Exception) {
                    failed++
                }

                if (i % 10 == 0 || i == tiles.size - 1) {
                    val pct = (i + 1) * 100 / tiles.size
                    runOnUiThread {
                        progressBar.progress = i + 1
                        zoomText.text = "zoom $zoom  |  $pct%  (${i + 1}/${tiles.size})"
                        countText.text = "ดาวน์โหลด: $downloaded  |  มีแล้ว: $skipped  |  ผิดพลาด: $failed"
                    }
                }
            }

            runOnUiThread {
                dialog.dismiss()
                downloadMapButton.isEnabled = true
                AlertDialog.Builder(this)
                    .setTitle("ดาวน์โหลดเสร็จแล้ว!")
                    .setMessage(
                        "ดาวน์โหลดใหม่: $downloaded tiles\n" +
                        "มีอยู่แล้ว (ข้าม): $skipped tiles\n" +
                        "ผิดพลาด: $failed tiles\n\n" +
                        "แผนที่ทั้ง campus มจพ. ปราจีนบุรี\nพร้อมใช้ offline ได้แล้ว!"
                    )
                    .setPositiveButton("ตกลง", null)
                    .show()
            }
        }.start()
    }

    private fun buildTileList(
        south: Double, north: Double, west: Double, east: Double,
        zoomMin: Int, zoomMax: Int
    ): List<Long> {
        val list = mutableListOf<Long>()
        for (zoom in zoomMin..zoomMax) {
            val minX = lon2tile(west, zoom)
            val maxX = lon2tile(east, zoom)
            val minY = lat2tile(north, zoom) // north = smaller Y in tile coords
            val maxY = lat2tile(south, zoom)
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    list.add(MapTileIndex.getTileIndex(zoom, x, y))
                }
            }
        }
        return list
    }

    private fun countTiles(
        south: Double, north: Double, west: Double, east: Double,
        zoomMin: Int, zoomMax: Int
    ): Int {
        var total = 0
        for (zoom in zoomMin..zoomMax) {
            val dx = lon2tile(east, zoom) - lon2tile(west, zoom) + 1
            val dy = lat2tile(south, zoom) - lat2tile(north, zoom) + 1
            total += dx * dy
        }
        return total
    }

    // แปลง longitude → tile X (OSM tile math)
    private fun lon2tile(lon: Double, zoom: Int): Int =
        ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

    // แปลง latitude → tile Y (OSM tile math — lat ใหญ่ = Y เล็ก)
    private fun lat2tile(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
    }

    // ตรวจสอบว่ามีอินเทอร์เน็ตหรือไม่
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ───────────────────────── Lifecycle ─────────────────────────

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (hasGpsPermission()) startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }
}
