package id.pdfmaps

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { PdfMapsTheme { MapApp() } }
    }

    override fun onStart() {
        super.onStart()
        (application as PdfMapsApp).state.startGps()
    }

    override fun onStop() {
        super.onStop()
        val st = (application as PdfMapsApp).state
        if (!st.needsBackground) st.stopGps()
    }
}

@Composable
fun MapApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember { (ctx.applicationContext as PdfMapsApp).state }

    var showMaps by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }
    var showBt by remember { mutableStateOf(false) }
    var showUnit by remember { mutableStateOf(false) }
    var showProjects by remember { mutableStateOf(false) }
    var showGoto by remember { mutableStateOf(false) }
    var showOsm by remember { mutableStateOf(false) }
    var showSaveDraft by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    var dataMenu by remember { mutableStateOf(false) }

    // ---------- Izin ----------
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) st.startGps()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val btPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) showBt = true else st.status = "Izin Bluetooth ditolak"
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) st.startGps() else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (st.bitmap == null) st.store.lastMapId?.let { st.openMap(it) }
    }

    // ---------- Kompas ----------
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                st.heading = ((Math.toDegrees(ori[0].toDouble()) + 360.0) % 360.0).toFloat()
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    // Service latar belakang untuk rekam track / geofence
    LaunchedEffect(st.needsBackground) { TrackingService.sync(ctx, st.needsBackground) }

    // Render ulang PDF dengan resolusi penuh setelah zoom/geser berhenti
    LaunchedEffect(st.zoom, st.offset, st.rotation, st.viewSize, st.bitmap) {
        delay(350)
        st.renderDetail()
    }

    // ---------- Picker file ----------
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { st.importMap(u, false) }
    }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { st.importMap(u, true) }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        scope.launch { st.importPhotos(us) }
    }
    val dataPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { st.importData(u) }
    }
    val gpxOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { u ->
        scope.launch { st.exportTo(u, "gpx") }
    }
    val kmlOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { u ->
        scope.launch { st.exportTo(u, "kml") }
    }
    val csvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u ->
        scope.launch { st.exportTo(u, "csv") }
    }
    val kmzOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kmz")) { u ->
        scope.launch { st.exportTo(u, "kmz") }
    }
    val shpOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        scope.launch { st.exportTo(u, "shp") }
    }
    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        val t = r.contents
        if (t != null) {
            if (t.startsWith("http://") || t.startsWith("https://")) scope.launch { st.importUrl(t.trim()) }
            else st.status = "QR bukan alamat web (URL)"
        }
    }

    val fmtLabel = when (st.store.coordFormat) { 1 -> "DMS"; 2 -> "UTM"; 3 -> "MGRS"; else -> "DD" }
    val l = st.loc
    val ctxBar = st.mode != Mode.NONE || st.recording || st.averaging
    val panelColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val tight = PaddingValues(horizontal = 4.dp, vertical = 12.dp)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        // ---------- Peta (layar penuh) ----------
        if (st.osmMode) {
            OsmMapView(st)
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFD81B60), modifier = Modifier.align(Alignment.Center).size(20.dp))
        } else if (st.bitmap != null) {
            MapCanvas(st)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Image(painterResource(R.drawable.logo_app), contentDescription = "Logo", modifier = Modifier.size(160.dp))
                    Text(
                        "Belum ada peta",
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Impor PDF peta (GeoPDF), gambar, atau GeoTIFF.\nPosisi GPS tampil di peta tanpa internet.",
                        Modifier.padding(top = 8.dp, bottom = 20.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showMaps = true }) { Text("Impor peta") }
                    OutlinedButton(onClick = { scope.launch { st.addBasemap() } }, Modifier.padding(top = 8.dp)) { Text("Buka peta Indonesia") }
                    OutlinedButton(onClick = { showOsm = true }, Modifier.padding(top = 8.dp)) { Text("Peta jalan offline") }
                }
            }
        }

        // ---------- Atas: bar judul gaya Avenza ----------
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showMaps = true }) { Icon(Icons.Filled.Menu, contentDescription = "Daftar peta", tint = MaterialTheme.colorScheme.onPrimary) }
                    Column(Modifier.weight(1f).clickable { showProjects = true }) {
                        Text(
                            if (st.osmMode) "Peta jalan offline" else st.currentMap?.name ?: "PDF Maps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            st.activeProject.name + "  ▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                    if (st.pageCount > 1) {
                        TextButton(onClick = { st.currentMapId?.let { id -> scope.launch { st.openMap(id, st.page - 1) } } }, enabled = st.page > 0) { Text("◀", color = MaterialTheme.colorScheme.onPrimary) }
                        Text("${st.page + 1}/${st.pageCount}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { st.currentMapId?.let { id -> scope.launch { st.openMap(id, st.page + 1) } } }, enabled = st.page < st.pageCount - 1) { Text("▶", color = MaterialTheme.colorScheme.onPrimary) }
                    }
                    IconButton(onClick = { showLayers = true }) { Icon(Icons.Filled.List, contentDescription = "Layer", tint = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if ((st.status.isNotBlank() && st.bitmap == null && !st.osmMode) || st.navInfo() != null) {
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(14.dp),
                            shadowElevation = 3.dp,
                        ) {
                            Text(
                                buildString { append(st.status); st.navInfo()?.let { append("\n$it") } },
                                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    st.alert?.let { msg ->
                        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp) {
                            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔔 $msg", Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium)
                                TextButton(onClick = { st.alert = null }) { Text("OK") }
                            }
                        }
                    }
                }
                if (st.bitmap != null && !st.osmMode) CompassButton(st)
            }
        }

        // ---------- Bawah: skala, bar konteks, menu utama ----------
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (!st.osmMode) ScaleBar(st, Modifier.align(Alignment.BottomStart))
                if ((st.bitmap != null || st.osmMode) && st.mode == Mode.NONE) {
                    Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LocateButton(st.follow) { st.toggleFollow() }
                        FloatingActionButton(
                            onClick = { if (st.osmMode) st.pendingPoint = st.osmCenter?.invoke() else st.changeMode(Mode.ADD_POINT) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) { Icon(Icons.Filled.Add, contentDescription = "Tambah titik") }
                    }
                }
            }
            if (l != null) {
                Surface(Modifier.fillMaxWidth().clickable { st.cycleCoordFormat() }, shape = RoundedCornerShape(14.dp), color = panelColor, contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 4.dp) {
                    Text(
                        "$fmtLabel  ${GeoMath.fmtCoord(l.latitude, l.longitude, st.store.coordFormat)}  ±%.0f m".format(l.accuracy),
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (ctxBar) {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 2.dp)) {
                        Text(
                            when {
                                st.mode == Mode.CALIBRATE -> "Kalibrasi: tap titik di peta (manual: ${st.manualPoints.size}, min 3)"
                                st.mode == Mode.ADD_POINT -> "Tap peta untuk menaruh titik"
                                st.mode == Mode.MOVE -> "Tap peta untuk memindahkan titik"
                                st.mode == Mode.RULER -> st.rulerInfo()
                                st.mode == Mode.MEASURE_LINE || st.mode == Mode.MEASURE_AREA ->
                                    st.draftInfo().ifBlank { "Tap peta untuk menambah titik" }
                                st.recording -> "Rekam: " + GeoMath.fmtDist(GeoMath.length(st.trackPts)) + " · ${st.trackPts.size} titik"
                                st.averaging -> "Rata-rata GPS: ${st.avgFixes.size}/20"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                            when {
                                st.mode == Mode.CALIBRATE || st.mode == Mode.ADD_POINT || st.mode == Mode.MOVE -> {
                                    TextButton(onClick = { st.mode = Mode.NONE; st.moveId = null }) { OneLine("Selesai") }
                                }
                                st.mode == Mode.RULER -> {
                                    TextButton(onClick = { st.setUnit((st.store.unit + 1) % 6) }) { OneLine("Satuan: ${GeoMath.unitSym(st.store.unit)}") }
                                    TextButton(onClick = { st.ruler = emptyList() }, enabled = st.ruler.isNotEmpty()) { OneLine("Ulang") }
                                    TextButton(onClick = { st.mode = Mode.NONE; st.ruler = emptyList() }) { OneLine("Selesai") }
                                }
                                st.mode == Mode.MEASURE_LINE || st.mode == Mode.MEASURE_AREA -> {
                                    TextButton(onClick = { st.setUnit((st.store.unit + 1) % 6) }) { OneLine("Satuan: ${GeoMath.unitSym(st.store.unit)}") }
                                    TextButton(onClick = { st.undoDraft() }, enabled = st.draft.isNotEmpty()) { OneLine("Undo") }
                                    TextButton(onClick = { showSaveDraft = true }, enabled = st.canSaveDraft()) { OneLine("Simpan") }
                                    TextButton(onClick = { st.mode = Mode.NONE; st.draft = emptyList() }) { OneLine("Batal") }
                                }
                                st.recording -> {
                                    TextButton(onClick = { st.stopRecording() }) { OneLine("Stop & simpan") }
                                }
                                st.averaging -> {
                                    TextButton(onClick = { st.cancelAveraging() }) { OneLine("Batal") }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = panelColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavItem(Icons.Filled.Place, "Peta", Modifier.weight(1f)) { showMaps = true }
                    NavItem(Icons.Filled.List, "Layer", Modifier.weight(1f)) { showLayers = true }
                    Box(Modifier.weight(1f)) {
                        NavItem(Icons.Filled.Build, "Alat", Modifier.fillMaxWidth(), enabled = st.bitmap != null && !st.osmMode) { toolsMenu = true }
                        DropdownMenu(expanded = toolsMenu, onDismissRequest = { toolsMenu = false }) {
                            DropdownMenuItem(text = { Text("Format koordinat: $fmtLabel") }, onClick = { toolsMenu = false; st.cycleCoordFormat() })
            DropdownMenuItem(text = { Text("Tambah titik (tap peta)") }, onClick = { toolsMenu = false; st.changeMode(Mode.ADD_POINT) })
            DropdownMenuItem(text = { Text("Tambah titik di posisi saya") }, onClick = { toolsMenu = false; st.pointHere() })
            DropdownMenuItem(text = { Text("Rata-rata GPS (20 fix)") }, onClick = { toolsMenu = false; st.startAveraging() })
            DropdownMenuItem(text = { Text("Ukur jarak / gambar garis") }, onClick = { toolsMenu = false; st.changeMode(Mode.MEASURE_LINE) })
            DropdownMenuItem(text = { Text("Ukur luas / gambar area") }, onClick = { toolsMenu = false; st.changeMode(Mode.MEASURE_AREA) })
            DropdownMenuItem(text = { Text("Penggaris (2 titik)") }, onClick = { toolsMenu = false; st.changeMode(Mode.RULER) })
            DropdownMenuItem(text = { Text("Satuan ukur: ${GeoMath.UNIT_NAMES[st.store.unit]}") }, onClick = { toolsMenu = false; showUnit = true })
            DropdownMenuItem(
                text = { Text(if (st.rotateLocked) "Putar peta: terkunci" else "Putar peta: bebas") },
                onClick = { toolsMenu = false; st.toggleRotateLock() },
            )
            DropdownMenuItem(text = { Text("Utara ke atas") }, onClick = { toolsMenu = false; st.resetNorth() })
            DropdownMenuItem(
                text = { Text(if (st.recording) "Stop rekam track" else "Rekam track GPS") },
                onClick = { toolsMenu = false; if (st.recording) st.stopRecording() else st.startRecording() },
            )
            DropdownMenuItem(text = { Text("Foto bergeotag → titik") }, onClick = { toolsMenu = false; photoPicker.launch(arrayOf("image/*")) })
            DropdownMenuItem(text = { Text("Pusatkan ke posisi saya") }, onClick = { toolsMenu = false; st.centerOnMe() })
            DropdownMenuItem(text = { Text("Ke koordinat…") }, onClick = { toolsMenu = false; showGoto = true })
            DropdownMenuItem(
                text = { Text("GPS Bluetooth…") },
                onClick = {
                    toolsMenu = false
                    if (Build.VERSION.SDK_INT >= 31 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) else showBt = true
                },
            )
            DropdownMenuItem(
                text = { Text(if (st.store.bgGeofence) "Geofence di latar belakang: aktif" else "Geofence di latar belakang: mati") },
                onClick = { toolsMenu = false; st.toggleBgGeofence() },
            )
            DropdownMenuItem(text = { Text("Kalibrasi manual") }, onClick = { toolsMenu = false; st.changeMode(Mode.CALIBRATE) })
            DropdownMenuItem(text = { Text("Hapus kalibrasi manual") }, onClick = { toolsMenu = false; st.setManual(emptyList()) })
            if (st.navTarget != null) {
                DropdownMenuItem(text = { Text("Berhenti navigasi") }, onClick = { toolsMenu = false; st.navTarget = null })
            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        NavItem(Icons.Filled.Share, "Data", Modifier.fillMaxWidth()) { dataMenu = true }
                        DropdownMenu(expanded = dataMenu, onDismissRequest = { dataMenu = false }) {
            DropdownMenuItem(text = { Text("Ekspor GPX") }, onClick = { dataMenu = false; gpxOut.launch("fitur.gpx") })
            DropdownMenuItem(text = { Text("Ekspor KML") }, onClick = { dataMenu = false; kmlOut.launch("fitur.kml") })
            DropdownMenuItem(text = { Text("Ekspor KMZ") }, onClick = { dataMenu = false; kmzOut.launch("fitur.kmz") })
            DropdownMenuItem(text = { Text("Ekspor Shapefile (ZIP)") }, onClick = { dataMenu = false; shpOut.launch("fitur_shp.zip") })
            DropdownMenuItem(text = { Text("Ekspor CSV") }, onClick = { dataMenu = false; csvOut.launch("fitur.csv") })
            DropdownMenuItem(text = { Text("Impor KML / KMZ / SHP / GPX / CSV / ZIP") }, onClick = { dataMenu = false; dataPicker.launch(arrayOf("*/*")) })
                        }
                    }
                }
            }
        }
    }

    // ---------- Dialog ----------
    if (showMaps) {
        MapsDialog(
            st, scope,
            onPdf = { pdfPicker.launch(arrayOf("application/pdf")); showMaps = false },
            onImage = { imgPicker.launch(arrayOf("image/*", "application/octet-stream")); showMaps = false },
            onQr = { showMaps = false; qrScanner.launch(ScanOptions().setPrompt("Arahkan ke QR peta").setBeepEnabled(false)) },
            onUrl = { showMaps = false; showUrl = true },
            onOsm = { showMaps = false; showOsm = true },
            onClose = { showMaps = false },
        )
    }
    if (showLayers) LayersDialog(st) { showLayers = false }
    if (showBt) BtDialog(st) { showBt = false }
    if (showUnit) UnitDialog(st) { showUnit = false }
    if (showProjects) ProjectsDialog(st, scope) { showProjects = false }
    if (showOsm) OsmDialog(st) { showOsm = false }
    if (showGoto) TextInputDialog("Ke koordinat", "lintang, bujur (mis. -6.2, 106.8)", "", { v -> st.goTo(v); showGoto = false }, { showGoto = false })
    if (showUrl) {
        TextInputDialog("Impor dari URL", "https://…/peta.pdf", "", { u ->
            showUrl = false
            if (u.isNotBlank()) scope.launch { st.importUrl(u.trim()) }
        }, { showUrl = false })
    }
    if (showSaveDraft) {
        val isArea = st.mode == Mode.MEASURE_AREA
        TextInputDialog(
            if (isArea) "Simpan area" else "Simpan garis", "Nama",
            st.defaultName(if (isArea) FType.AREA else FType.LINE),
            { n -> showSaveDraft = false; st.saveDraft(n) }, { showSaveDraft = false },
        )
    }
    st.pendingPoint?.let { p ->
        TextInputDialog(
            "Tambah titik", "Nama", st.defaultName(FType.POINT),
            { n -> st.savePoint(p, n) }, { st.pendingPoint = null },
            hint = GeoMath.fmtCoord(p.lat, p.lon, st.store.coordFormat),
        )
    }
    st.pendingCalib?.let { px -> CalibDialog(st, px) }
    st.store.features.firstOrNull { it.id == st.selectedId }?.let { f ->
        FeatureDialog(st, f) { st.selectedId = null }
    }
}

// ======================================================================
//  Kanvas peta
// ======================================================================

private fun DrawScope.drawSymbol(sym: Int, c: Offset, r: Float, col: Color) {
    val w = Color.White
    when (sym) {
        1 -> {
            drawRect(w, Offset(c.x - r * 1.25f, c.y - r * 1.25f), androidx.compose.ui.geometry.Size(r * 2.5f, r * 2.5f))
            drawRect(col, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2f, r * 2f))
        }
        2 -> {
            fun tri(k: Float) = Path().apply {
                moveTo(c.x, c.y - k); lineTo(c.x + k, c.y + k * 0.85f); lineTo(c.x - k, c.y + k * 0.85f); close()
            }
            drawPath(tri(r * 1.35f), w); drawPath(tri(r * 1.05f), col)
        }
        3 -> {
            fun dia(k: Float) = Path().apply {
                moveTo(c.x, c.y - k); lineTo(c.x + k, c.y); lineTo(c.x, c.y + k); lineTo(c.x - k, c.y); close()
            }
            drawPath(dia(r * 1.45f), w); drawPath(dia(r * 1.15f), col)
        }
        4 -> {
            drawLine(w, Offset(c.x - r * 1.3f, c.y), Offset(c.x + r * 1.3f, c.y), strokeWidth = r * 1.1f)
            drawLine(w, Offset(c.x, c.y - r * 1.3f), Offset(c.x, c.y + r * 1.3f), strokeWidth = r * 1.1f)
            drawLine(col, Offset(c.x - r * 1.2f, c.y), Offset(c.x + r * 1.2f, c.y), strokeWidth = r * 0.6f)
            drawLine(col, Offset(c.x, c.y - r * 1.2f), Offset(c.x, c.y + r * 1.2f), strokeWidth = r * 0.6f)
        }
        else -> {
            drawCircle(w, r * 1.25f, c)
            drawCircle(col, r, c)
        }
    }
}

private fun pathOf(o: List<Offset>, close: Boolean): Path = Path().apply {
    moveTo(o[0].x, o[0].y)
    for (i in 1 until o.size) lineTo(o[i].x, o[i].y)
    if (close) close()
}

@Composable
fun MapCanvas(st: AppState) {
    val bmp = st.bitmap ?: return
    val image = remember(bmp) { bmp.asImageBitmap() }
    val dImg = remember(st.detail) { st.detail?.bmp?.asImageBitmap() }
    val measurer = rememberTextMeasurer()
    val tr = st.transform
    val shapes = remember(st.store.features, tr) {
        if (tr == null) emptyMap() else st.store.features.associate { f -> f.id to f.pts.map { tr.off(it) } }
    }

    Box(Modifier.fillMaxSize()) {
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { st.onViewSize(it) }
            .pointerInput(Unit) {
                detectTransformGestures { c, pan, gz, gr -> st.transformBy(c, pan, gz, gr) }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap -> st.onTap(st.toMap(tap)) }
            }
    ) {
        withTransform({
            translate(st.offset.x, st.offset.y)
            rotate(st.rotation, pivot = Offset.Zero)
            scale(st.zoom, st.zoom, pivot = Offset.Zero)
        }) {
            drawImage(image)
            val dt = st.detail
            if (dt != null && dImg != null) {
                withTransform({
                    translate(dt.x0, dt.y0)
                    scale(1f / dt.zoom, 1f / dt.zoom, pivot = Offset.Zero)
                }) { drawImage(dImg) }
            }
            val z = st.zoom
            val r = 9f / z
            val t = st.transform
            val fontPx = (13f / z).coerceIn(2f, 400f)
            val unitTick = st.store.unit
            val nAng = st.northAngle()

            // titik kalibrasi
            for (c in st.autoPoints + st.manualPoints) {
                drawCircle(Color.Red, r, Offset(c.px.toFloat(), c.py.toFloat()), style = Stroke(3f / z))
            }
            if (t == null) return@withTransform

            // fitur
            val layers = st.store.layers.associateBy { it.id }
            for (f in st.store.features) {
                val layer = layers[f.layerId] ?: continue
                if (!layer.visible) continue
                val col = Color(if (f.color != 0L) f.color else layer.color)
                val wpx = when (f.width) { 1 -> 2f; 2 -> 4f; 3 -> 8f; else -> if (f.type == FType.AREA) 3f else 4f }
                val o = shapes[f.id] ?: continue
                when (f.type) {
                    FType.POINT -> {
                        val c = o[0]
                        if (f.radius > 0) {
                            val rp = (c - t.off(Pt(f.pts[0].lat + f.radius / 111320.0, f.pts[0].lon))).getDistance()
                            drawCircle(col.copy(alpha = 0.15f), rp, c)
                            drawCircle(col, rp, c, style = Stroke(2f / z))
                        }
                        drawSymbol(f.symbol, c, r, col)
                        val lp = Offset(c.x + r * 1.6f, c.y - r)
                        withTransform({ rotate(-st.rotation, pivot = lp) }) {
                            drawText(measurer, f.name, lp, TextStyle(fontSize = fontPx.sp, color = Color.Black))
                        }
                    }
                    FType.LINE, FType.TRACK ->
                        if (o.size >= 2) drawPath(pathOf(o, false), col, style = Stroke(wpx / z))
                    FType.AREA ->
                        if (o.size >= 3) {
                            val p = pathOf(o, true)
                            drawPath(p, col.copy(alpha = 0.25f))
                            drawPath(p, col, style = Stroke(wpx / z))
                        }
                }
            }

            // gambar/ukur yang sedang dikerjakan
            if (st.draft.isNotEmpty()) {
                val o = st.draft.map { t.off(it) }
                val blue = Color(0xFF1565C0)
                if (o.size >= 2) {
                    val closed = st.mode == Mode.MEASURE_AREA && o.size >= 3
                    val p = pathOf(o, closed)
                    if (closed) drawPath(p, blue.copy(alpha = 0.2f))
                    drawPath(p, blue, style = Stroke(3f / z))
                }
                o.forEach { drawCircle(blue, r * 0.8f, it) }
            }

            // penggaris
            if (st.ruler.isNotEmpty()) {
                val orange = Color(0xFFEF6C00)
                val ro = st.ruler.map { t.off(it) }
                if (ro.size == 2) {
                    drawLine(orange, ro[0], ro[1], strokeWidth = 4f / z)
                    val total = GeoMath.dist(st.ruler[0], st.ruler[1])
                    val dir = ro[1] - ro[0]
                    val len = dir.getDistance()
                    if (total > 0.0 && len > 0f) {
                        val uf = GeoMath.unitFactor(unitTick)
                        val stepM = GeoMath.niceStep(total / uf / 8.0) * uf
                        val n = (total / stepM).toInt()
                        if (n in 1..80) {
                            val nx = -dir.y / len
                            val ny = dir.x / len
                            for (k in 0..n) {
                                val p = ro[0] + dir * (k * stepM / total).toFloat()
                                val tl = (if (k % 5 == 0) 11f else 6f) / z
                                drawLine(orange, Offset(p.x - nx * tl, p.y - ny * tl), Offset(p.x + nx * tl, p.y + ny * tl), 2f / z)
                            }
                        }
                        val mid = ro[0] + dir * 0.5f
                        val lp = Offset(mid.x + r, mid.y - r * 2.2f)
                        withTransform({ rotate(-st.rotation, pivot = lp) }) {
                            drawText(measurer, GeoMath.fmtDist(total, unitTick), lp, TextStyle(fontSize = fontPx.sp, color = Color(0xFF7A3500)))
                        }
                    }
                }
                ro.forEach {
                    drawCircle(Color.White, r * 1.1f, it)
                    drawCircle(orange, r * 0.8f, it)
                }
            }

            // track yang sedang direkam
            if (st.trackPts.size >= 2) {
                drawPath(pathOf(st.trackPts.map { t.off(it) }, false), Color.Red, style = Stroke(4f / z))
            }

            // penanda "Ke koordinat"
            st.gotoPt?.let { g ->
                val c = t.off(g)
                val k = r * 2.2f
                val mg = Color(0xFFD81B60)
                drawLine(Color.White, Offset(c.x - k, c.y), Offset(c.x + k, c.y), strokeWidth = 5f / z)
                drawLine(Color.White, Offset(c.x, c.y - k), Offset(c.x, c.y + k), strokeWidth = 5f / z)
                drawLine(mg, Offset(c.x - k, c.y), Offset(c.x + k, c.y), strokeWidth = 2.5f / z)
                drawLine(mg, Offset(c.x, c.y - k), Offset(c.x, c.y + k), strokeWidth = 2.5f / z)
                drawCircle(mg, k * 0.7f, c, style = Stroke(2.5f / z))
            }

            // posisi GPS
            val l = st.loc
            if (l != null) {
                val c = t.off(Pt(l.latitude, l.longitude))
                st.navFeature()?.let { nf ->
                    drawLine(Color(0xFFD81B60), c, t.off(st.target(nf)), strokeWidth = 3f / z)
                }
                val blue = Color(0xFF1A73E8)
                // lingkaran akurasi
                val accPx = (c - t.off(Pt(l.latitude + l.accuracy / 111320.0, l.longitude))).getDistance()
                if (accPx > r * 1.8f) {
                    drawCircle(blue.copy(alpha = 0.14f), accPx, c)
                    drawCircle(blue.copy(alpha = 0.45f), accPx, c, style = Stroke(1.5f / z))
                }
                // kerucut arah hadap
                st.heading?.let { h ->
                    val a0 = Math.toRadians((h + nAng).toDouble())
                    val half = Math.toRadians(32.0)
                    val rad = r * 6f
                    val cone = Path().apply {
                        moveTo(c.x, c.y)
                        for (i in 0..8) {
                            val ang = a0 - half + (2 * half) * i / 8.0
                            lineTo(c.x + (sin(ang) * rad).toFloat(), c.y - (cos(ang) * rad).toFloat())
                        }
                        close()
                    }
                    drawPath(cone, blue.copy(alpha = 0.30f))
                }
                // titik posisi: cincin bayangan, putih, biru
                drawCircle(Color(0x33000000), r * 1.75f, c)
                drawCircle(Color.White, r * 1.55f, c)
                drawCircle(blue, r * 1.1f, c)
            }
        }
    }
    }
}

/** Tombol "pusatkan ke posisi saya" ala Google Maps; biru penuh saat peta mengikuti GPS. */
@Composable
fun LocateButton(active: Boolean, onClick: () -> Unit) {
    val blue = Color(0xFF1A73E8)
    val ring = if (active) blue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Surface(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension * 0.22f
            drawCircle(ring, r, c, style = Stroke(size.minDimension * 0.045f))
            drawCircle(if (active) blue else ring, r * 0.5f, c)
            val k = r * 1.45f
            val w = size.minDimension * 0.045f
            drawLine(ring, Offset(c.x, c.y - k), Offset(c.x, c.y - r), strokeWidth = w)
            drawLine(ring, Offset(c.x, c.y + r), Offset(c.x, c.y + k), strokeWidth = w)
            drawLine(ring, Offset(c.x - k, c.y), Offset(c.x - r, c.y), strokeWidth = w)
            drawLine(ring, Offset(c.x + r, c.y), Offset(c.x + k, c.y), strokeWidth = w)
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val col = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = col, modifier = Modifier.size(24.dp))
        Text(label, color = col, fontSize = 11.sp, maxLines = 1, softWrap = false, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun OneLine(text: String, color: Color = Color.Unspecified, size: androidx.compose.ui.unit.TextUnit = 13.sp) {
    Text(text, color = color, fontSize = size, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
fun CompassButton(st: AppState, modifier: Modifier = Modifier) {
    val ang = st.northAngle() + st.rotation
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier.size(56.dp).clickable { st.resetNorth() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f * 0.5f
            withTransform({ rotate(ang, pivot = c) }) {
                val north = Path().apply {
                    moveTo(c.x, c.y - r); lineTo(c.x - r * 0.4f, c.y); lineTo(c.x + r * 0.4f, c.y); close()
                }
                val south = Path().apply {
                    moveTo(c.x, c.y + r); lineTo(c.x - r * 0.4f, c.y); lineTo(c.x + r * 0.4f, c.y); close()
                }
                drawPath(north, Color(0xFFD32F2F))
                drawPath(south, Color(0xFF90A4AE))
                val lay = measurer.measure("U", TextStyle(fontSize = 11.sp, color = labelColor))
                drawText(lay, topLeft = Offset(c.x - lay.size.width / 2f, c.y - r - lay.size.height - 1f))
            }
        }
    }
}

@Composable
fun ScaleBar(st: AppState, modifier: Modifier = Modifier) {
    val t = st.transform ?: return
    if (st.viewSize.width <= 0) return
    val u = st.store.unit
    val density = LocalDensity.current.density
    val c = st.toMap(Offset(st.viewSize.width / 2f, st.viewSize.height / 2f))
    val a = t.toLatLon(c.x.toDouble(), c.y.toDouble())
    val b = t.toLatLon(c.x.toDouble() + 1.0, c.y.toDouble())
    val mapPx = GeoMath.dist(Pt(a.first, a.second), Pt(b.first, b.second))
    val mPerPx = mapPx / st.zoom
    if (mPerPx <= 0.0 || mPerPx.isNaN()) return
    val targetM = 110.0 * density * mPerPx
    val uf = if (u == 0) (if (targetM >= 1000.0) 1000.0 else 1.0) else GeoMath.unitFactor(u)
    val sym = if (u == 0) (if (targetM >= 1000.0) "km" else "m") else GeoMath.unitSym(u)
    val v = GeoMath.niceStep(targetM / uf)
    val widthDp = (v * uf / mPerPx / density).toFloat().coerceIn(20f, 260f)
    val label = String.format(java.util.Locale.US, "%.3f", v).trimEnd('0').trimEnd('.') + " " + sym
    Surface(
        modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(widthDp.dp).height(3.dp).background(MaterialTheme.colorScheme.onSurface))
        }
    }
}

@Composable
fun ProjectsDialog(st: AppState, scope: kotlinx.coroutines.CoroutineScope, onClose: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Proyek") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Setiap proyek punya peta (PDF), layer, dan titik sendiri.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                st.projects.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { st.switchProject(p.id) }; onClose() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(p.id == st.activeProjectId, { scope.launch { st.switchProject(p.id) }; onClose() })
                        Text(p.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { renameId = p.id; renameText = p.name }) { OneLine("Ubah") }
                        TextButton(onClick = { deleteId = p.id }) { OneLine("Hapus") }
                    }
                }
                val verCtx = LocalContext.current
                val ver = remember { try { verCtx.packageManager.getPackageInfo(verCtx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
                Text("PDF Maps v$ver", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Nama proyek baru") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { st.createProject(newName) }; onClose() }) { OneLine("Buat") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
    renameId?.let { id ->
        TextInputDialog("Ubah nama proyek", "Nama", renameText, { n -> st.renameProject(id, n); renameId = null }, { renameId = null })
    }
    deleteId?.let { id ->
        val nm = st.projects.firstOrNull { it.id == id }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Hapus proyek?") },
            text = { Text("Semua peta, layer, dan titik di proyek \"$nm\" akan dihapus permanen.") },
            confirmButton = { TextButton(onClick = { scope.launch { st.deleteProject(id) }; deleteId = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Batal") } },
        )
    }
}

@Composable
fun UnitDialog(st: AppState, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Satuan ukur") },
        text = {
            Column {
                GeoMath.UNIT_NAMES.forEachIndexed { i, n ->
                    Row(
                        Modifier.fillMaxWidth().clickable { st.setUnit(i); onClose() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(st.store.unit == i, { st.setUnit(i); onClose() })
                        Text(n)
                    }
                }
                Text(
                    "Berlaku untuk jarak, luas, penggaris, skala, dan navigasi.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}

// ======================================================================
//  Dialog
// ======================================================================

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onOk: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun CalibDialog(st: AppState, px: Offset) {
    var latText by remember(px) { mutableStateOf("") }
    var lonText by remember(px) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { st.pendingCalib = null },
        title = { Text("Koordinat titik ini") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latText, { latText = it }, label = { Text("Latitude (mis. -0.0263)") }, singleLine = true)
                OutlinedTextField(lonText, { lonText = it }, label = { Text("Longitude (mis. 109.3425)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lat = latText.replace(',', '.').toDoubleOrNull()
                val lon = lonText.replace(',', '.').toDoubleOrNull()
                if (lat != null && lon != null) {
                    st.setManual(st.manualPoints + ControlPoint(px.x.toDouble(), px.y.toDouble(), lat, lon))
                    st.pendingCalib = null
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { st.pendingCalib = null }) { Text("Batal") } },
    )
}

@Composable
fun MapsDialog(
    st: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    onPdf: () -> Unit,
    onImage: () -> Unit,
    onUrl: () -> Unit,
    onOsm: () -> Unit,
    onQr: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Peta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPdf) { Text("Impor PDF") }
                    OutlinedButton(onClick = onImage) { Text("Gambar/GeoTIFF") }
                    OutlinedButton(onClick = onUrl) { Text("URL") }
                    OutlinedButton(onClick = onQr) { Text("QR") }
                    OutlinedButton(onClick = { scope.launch { st.addBasemap() }; onClose() }) { Text("Peta Indonesia") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOsm) { Text("Peta jalan offline (unduh)") }
                }
                if (st.store.maps.isEmpty()) Text("Belum ada peta tersimpan.", style = MaterialTheme.typography.bodySmall)
                st.store.maps.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.name + if (m.id == st.currentMapId) "  ✓" else "",
                            Modifier.weight(1f).clickable { scope.launch { st.openMap(m.id) }; onClose() }.padding(vertical = 10.dp),
                        )
                        TextButton(onClick = { st.deleteMap(m.id) }) { Text("Hapus") }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}

@Composable
fun LayersDialog(st: AppState, onClose: () -> Unit) {
    var attrLayer by remember { mutableStateOf<String?>(null) }
    var colorLayer by remember { mutableStateOf<String?>(null) }
    var renameLayerId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Layer & fitur") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(q, { q = it }, label = { Text("Cari fitur") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                st.store.layers.forEach { layer ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(layer.visible, { st.setLayerVisible(layer.id, it) })
                        RadioButton(layer.id == st.store.activeLayerId, { st.setActiveLayer(layer.id) })
                        Box(Modifier.size(18.dp).background(Color(layer.color), CircleShape).border(1.dp, Color.Gray, CircleShape).clickable { colorLayer = layer.id })
                        Text(layer.name, Modifier.weight(1f).padding(start = 6.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick = { colorLayer = layer.id }) { Text("Warna") }
                        TextButton(onClick = { renameLayerId = layer.id }) { Text("Ubah nama") }
                        TextButton(onClick = { attrLayer = layer.id }) { Text("Atribut") }
                        TextButton(onClick = { st.deleteLayer(layer.id) }) { Text("Hapus") }
                    }
                    st.store.features.filter { it.layerId == layer.id && (q.isBlank() || it.name.contains(q, true)) }.forEach { f ->
                        Text(
                            "• ${f.name} (${typeLabel(f.type)})",
                            Modifier.fillMaxWidth()
                                .clickable { st.selectedId = f.id; st.centerOnFeature(f); onClose() }
                                .padding(start = 24.dp, top = 6.dp, bottom = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
                Text("Kotak = tampil · bulat = layer aktif untuk fitur baru", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Layer baru") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (newName.isNotBlank()) { st.addLayer(newName.trim()); newName = "" } }) { Text("Tambah") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
    attrLayer?.let { id ->
        st.store.layers.firstOrNull { it.id == id }?.let { AttrFieldsDialog(st, it) { attrLayer = null } }
    }
    colorLayer?.let { id ->
        AlertDialog(
            onDismissRequest = { colorLayer = null },
            title = { Text("Warna layer") },
            text = { ColorRow(st.store.layers.firstOrNull { it.id == id }?.color ?: 0L, false) { c -> st.setLayerColor(id, c); colorLayer = null } },
            confirmButton = { TextButton(onClick = { colorLayer = null }) { Text("Tutup") } },
        )
    }
    renameLayerId?.let { id ->
        TextInputDialog("Ubah nama layer", "Nama", st.store.layers.firstOrNull { it.id == id }?.name ?: "", { n -> st.renameLayer(id, n); renameLayerId = null }, { renameLayerId = null })
    }
}

@Composable
fun ColorRow(selected: Long, allowDefault: Boolean, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowDefault) {
            OutlinedButton(onClick = { onPick(0L) }) { Text(if (selected == 0L) "✓ Ikut warna layer" else "Ikut warna layer") }
        }
        FEATURE_COLORS.toList().chunked(6).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowColors.forEach { c ->
                    Box(
                        Modifier.size(36.dp).background(Color(c), CircleShape)
                            .border(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                            .clickable { onPick(c) },
                    )
                }
            }
        }
    }
}

@Composable
fun AttrFieldsDialog(st: AppState, layer: Layer, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Atribut: ${layer.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Kolom isian tambahan untuk setiap fitur di layer ini.", style = MaterialTheme.typography.bodySmall)
                layer.fields.forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.weight(1f))
                        TextButton(onClick = { st.removeField(layer.id, k) }) { Text("Hapus") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nama atribut") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = { st.addField(layer.id, name); name = "" }) { Text("Tambah") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}

@Composable
fun BtDialog(st: AppState, onClose: () -> Unit) {
    val devices = remember { st.pairedDevices() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("GPS Bluetooth") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Pasangkan (pair) GPS eksternal di pengaturan Bluetooth HP dulu, lalu pilih di sini. Perangkat harus mengirim data NMEA lewat SPP.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (st.btName != null) {
                    Text("Tersambung: ${st.btName}")
                    TextButton(onClick = { st.disconnectBt(); onClose() }) { Text("Putuskan") }
                }
                if (devices.isEmpty()) Text("Tidak ada perangkat yang terpasang.")
                devices.forEach { d ->
                    Text(
                        st.deviceLabel(d),
                        Modifier.fillMaxWidth().clickable { st.connectBt(d); onClose() }.padding(vertical = 12.dp),
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}

@Composable
fun FeatureDialog(st: AppState, f: Feature, onClose: () -> Unit) {
    var name by remember(f.id) { mutableStateOf(f.name) }
    var notes by remember(f.id) { mutableStateOf(f.notes) }
    var attrs by remember(f.id) { mutableStateOf(f.attrs) }
    val fields = st.store.layers.firstOrNull { it.id == f.layerId }?.fields ?: emptyList()
    var radius by remember(f.id) { mutableStateOf(if (f.radius > 0) f.radius.toInt().toString() else "") }
    var color by remember(f.id) { mutableStateOf(f.color) }
    var symbol by remember(f.id) { mutableStateOf(f.symbol) }
    var width by remember(f.id) { mutableStateOf(f.width) }
    var layerId by remember(f.id) { mutableStateOf(f.layerId) }
    val thumb = remember(f.photo) { f.photo?.let { st.loadThumb(it) } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(f.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(st.describe(f), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") })
                fields.forEach { k ->
                    OutlinedTextField(attrs[k] ?: "", { v -> attrs = attrs + (k to v) }, label = { Text(k) }, singleLine = true)
                }
                if (f.type == FType.POINT) {
                    OutlinedTextField(
                        radius, { radius = it.filter { c -> c.isDigit() } },
                        label = { Text("Geofence (meter, kosong = mati)") }, singleLine = true,
                    )
                }
                Text("Layer", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    st.store.layers.forEach { ly ->
                        if (ly.id == layerId) Button(onClick = { layerId = ly.id }) { Text(ly.name) }
                        else OutlinedButton(onClick = { layerId = ly.id }) { Text(ly.name) }
                    }
                }
                Text("Warna", style = MaterialTheme.typography.labelMedium)
                ColorRow(color, true) { color = it }
                if (f.type == FType.POINT) {
                    Text("Simbol", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SYMBOL_NAMES.forEachIndexed { i, nm ->
                            if (i == symbol) Button(onClick = { symbol = i }) { Text(nm) }
                            else OutlinedButton(onClick = { symbol = i }) { Text(nm) }
                        }
                    }
                } else {
                    Text("Tebal garis", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WIDTH_NAMES.forEachIndexed { i, nm ->
                            if (i == width) Button(onClick = { width = i }) { Text(nm) }
                            else OutlinedButton(onClick = { width = i }) { Text(nm) }
                        }
                    }
                }
                if (thumb != null) {
                    Image(bitmap = thumb.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { st.centerOnFeature(f); onClose() }) { Text("Zoom") }
                    OutlinedButton(onClick = { st.navTarget = f.id; onClose() }) { Text("Navigasi") }
                    if (f.type == FType.POINT) {
                        OutlinedButton(onClick = { st.startMove(f.id); onClose() }) { Text("Pindah (tap peta)") }
                        OutlinedButton(onClick = { st.moveToMe(f); onClose() }) { Text("Ke posisiku") }
                    } else {
                        OutlinedButton(onClick = { st.convertFeature(f); onClose() }) { Text(if (f.type == FType.AREA) "Jadikan garis" else "Jadikan area") }
                    }
                    OutlinedButton(onClick = { st.deleteFeature(f.id); onClose() }) { Text("Hapus") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                st.updateFeature(
                    f.copy(name = name.ifBlank { f.name }, notes = notes, attrs = attrs, radius = radius.toDoubleOrNull() ?: 0.0, color = color, symbol = symbol, width = width, layerId = layerId)
                )
                onClose()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}
