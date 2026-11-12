package id.pdfmaps

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.mapsforge.core.graphics.Paint
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.overlay.Polygon
import org.mapsforge.map.layer.overlay.Polyline
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.InternalRenderTheme
import java.io.File

/** Peta jalan offline (OpenStreetMap, format Mapsforge) yang diunduh dari dalam aplikasi. */
object OsmMaps {
    class Region(val id: String, val name: String, val sizeMb: Int)
    class Info(val state: Int, val pct: Int, val msg: String) // 0 belum, 1 mengunduh, 2 siap, 3 gagal

    private const val BASE = "https://download.mapsforge.org/maps/v5/asia/"
    val REGIONS = listOf(
        Region("java", "Jawa", 880),
        Region("sumatra", "Sumatra", 295),
        Region("kalimantan", "Kalimantan", 160),
        Region("sulawesi", "Sulawesi", 171),
        Region("nusa-tenggara", "Nusa Tenggara", 175),
        Region("maluku", "Maluku", 50),
        Region("papua", "Papua", 59),
        Region("indonesia", "Seluruh Indonesia", 1700),
    )

    fun url(r: Region) = if (r.id == "indonesia") BASE + "indonesia.map" else BASE + "indonesia/${r.id}.map"
    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "osm").apply { mkdirs() }
    fun file(ctx: Context, r: Region) = File(dir(ctx), "${r.id}.map")
    private fun part(ctx: Context, r: Region) = File(dir(ctx), "${r.id}.part")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("osm", Context.MODE_PRIVATE)
    private fun dm(ctx: Context) = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun available(ctx: Context): List<File> {
        REGIONS.forEach { info(ctx, it) } // selesaikan unduhan yang rampung saat aplikasi tertutup
        val all = dir(ctx).listFiles { f -> f.name.endsWith(".map") && f.length() > 1000 }?.toList() ?: emptyList()
        val whole = all.firstOrNull { it.name == "indonesia.map" }
        return if (whole != null) listOf(whole) else all
    }

    fun freeMb(ctx: Context): Long = dir(ctx).usableSpace / (1024L * 1024L)

    fun start(ctx: Context, r: Region): String {
        if (freeMb(ctx) < r.sizeMb + 200) return "Penyimpanan tidak cukup: butuh ${r.sizeMb} MB, tersisa ${freeMb(ctx)} MB"
        return try {
            part(ctx, r).delete()
            val req = DownloadManager.Request(Uri.parse(url(r)))
                .setTitle("Peta jalan: ${r.name}")
                .setDescription("PDF Maps, ${r.sizeMb} MB")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(ctx, null, "osm/${r.id}.part")
            val id = dm(ctx).enqueue(req)
            prefs(ctx).edit().putLong(r.id, id).apply()
            "Mengunduh ${r.name}…"
        } catch (ex: Exception) {
            "Gagal memulai unduhan: ${ex.message}"
        }
    }

    fun info(ctx: Context, r: Region): Info {
        val id = prefs(ctx).getLong(r.id, -1L)
        if (id < 0) return if (file(ctx, r).exists()) Info(2, 100, "Siap") else Info(0, 0, "")
        try {
            dm(ctx).query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c == null || !c.moveToFirst()) {
                    prefs(ctx).edit().remove(r.id).apply()
                    return if (file(ctx, r).exists()) Info(2, 100, "Siap") else Info(0, 0, "")
                }
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                return when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val p = part(ctx, r)
                        if (p.exists()) { file(ctx, r).delete(); p.renameTo(file(ctx, r)) }
                        prefs(ctx).edit().remove(r.id).apply()
                        if (file(ctx, r).exists()) Info(2, 100, "Siap") else Info(3, 0, "File hasil unduhan tidak ditemukan")
                    }
                    DownloadManager.STATUS_FAILED -> {
                        prefs(ctx).edit().remove(r.id).apply()
                        part(ctx, r).delete()
                        Info(3, 0, "Gagal (kode $reason)")
                    }
                    DownloadManager.STATUS_PAUSED -> Info(1, pct, "Menunggu jaringan… $pct%")
                    DownloadManager.STATUS_PENDING -> Info(1, pct, "Antre…")
                    else -> Info(1, pct, "$pct% (${done / 1048576} / ${total / 1048576} MB)")
                }
            }
        } catch (ex: Exception) {
            return Info(3, 0, "Galat: ${ex.message}")
        }
    }

    fun remove(ctx: Context, r: Region) {
        val id = prefs(ctx).getLong(r.id, -1L)
        if (id >= 0) { try { dm(ctx).remove(id) } catch (_: Exception) {} }
        prefs(ctx).edit().remove(r.id).apply()
        part(ctx, r).delete()
        file(ctx, r).delete()
    }
}

@Composable
fun OsmDialog(st: AppState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val infos = remember(tick) { OsmMaps.REGIONS.map { it to OsmMaps.info(ctx, it) } }
    val anyReady = infos.any { it.second.state == 2 }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Peta jalan offline") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Peta jalan OpenStreetMap sampai jalan kecil. Unduh sekali (sebaiknya lewat Wi-Fi), lalu dipakai tanpa internet. " +
                        "Sisa penyimpanan: ${OsmMaps.freeMb(ctx)} MB.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                infos.forEach { (r, i) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name)
                            Text(
                                when (i.state) { 0 -> "${r.sizeMb} MB"; else -> i.msg + if (i.state == 2) " · ${r.sizeMb} MB" else "" },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (i.state == 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when (i.state) {
                            0, 3 -> TextButton(onClick = { msg = OsmMaps.start(ctx, r); tick++ }) { OneLine("Unduh") }
                            1 -> TextButton(onClick = { OsmMaps.remove(ctx, r); tick++ }) { OneLine("Batal") }
                            else -> TextButton(onClick = { OsmMaps.remove(ctx, r); st.osmVersion++; tick++ }) { OneLine("Hapus") }
                        }
                    }
                    HorizontalDivider()
                }
                Text("Data peta © kontributor OpenStreetMap", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = { st.osmVersion++; st.osmMode = true; st.follow = false; onClose() }, enabled = anyReady) { Text("Buka peta jalan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}

private class OsmHolder(val mapView: MapView) {
    var featLayers: List<org.mapsforge.map.layer.Layer> = emptyList()
    var storeRef: Store? = null
    var gpsDot: FixedPixelCircle? = null
    var gpsAcc: Circle? = null
}

private fun osmPaint(color: Int, width: Float, style: Style): Paint =
    AndroidGraphicFactory.INSTANCE.createPaint().apply {
        setColor(color)
        setStrokeWidth(width)
        setStyle(style)
    }

/** Tampilan peta jalan offline dengan posisi GPS dan semua fitur dari layer yang terlihat. */
@Composable
fun OsmMapView(st: AppState) {
    val ctx = LocalContext.current
    val files = remember(st.osmVersion) { OsmMaps.available(ctx) }
    if (files.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Belum ada peta jalan terunduh. Buka Peta > Peta jalan offline.")
        }
        return
    }
    val dens = ctx.resources.displayMetrics.density
    val holder = remember(files) {
        val mv = MapView(ctx)
        mv.isClickable = true
        mv.setBuiltInZoomControls(false)
        mv.mapScaleBar.isVisible = false
        mv.setZoomLevelMin(5.toByte())
        mv.setZoomLevelMax(21.toByte())
        val cache = AndroidUtil.createTileCache(
            ctx, "osmcache", mv.model.displayModel.tileSize, 1f, mv.model.frameBufferModel.overdrawFactor,
        )
        val multi = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        files.forEachIndexed { i, f -> multi.addMapDataStore(MapFile(f), i == 0, i == 0) }
        val tiles = TileRendererLayer(cache, multi, mv.model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
        tiles.setXmlRenderTheme(InternalRenderTheme.DEFAULT)
        mv.layerManager.layers.add(tiles)
        val l = st.loc
        val bb = multi.boundingBox()
        if (l != null && bb.contains(l.latitude, l.longitude)) {
            mv.setCenter(LatLong(l.latitude, l.longitude)); mv.setZoomLevel(15.toByte())
        } else {
            mv.setCenter(bb.centerPoint); mv.setZoomLevel(8.toByte())
        }
        OsmHolder(mv)
    }
    DisposableEffect(holder) {
        st.osmCenter = { holder.mapView.model.mapViewPosition.center.let { Pt(it.latitude, it.longitude) } }
        st.osmGoto = { p -> holder.mapView.setCenter(LatLong(p.lat, p.lon)) }
        onDispose {
            st.osmCenter = null
            st.osmGoto = null
            holder.mapView.destroyAll()
        }
    }
    val store = st.store
    val loc = st.loc
    val follow = st.follow
    AndroidView(factory = { holder.mapView }, modifier = Modifier.fillMaxSize(), update = { mv ->
        val layers = mv.layerManager.layers
        if (holder.storeRef !== store) {
            holder.featLayers.forEach { layers.remove(it, false) }
            val gf = AndroidGraphicFactory.INSTANCE
            val out = ArrayList<org.mapsforge.map.layer.Layer>()
            for (ly in store.layers) {
                if (!ly.visible) continue
                for (f in store.features) {
                    if (f.layerId != ly.id || f.pts.isEmpty()) continue
                    val col = (if (f.color != 0L) f.color else ly.color).toInt()
                    val w = (when (f.width) { 1 -> 2f; 2 -> 4f; 3 -> 7f; else -> 3.5f }) * dens
                    val pts = f.pts.map { LatLong(it.lat, it.lon) }
                    when (f.type) {
                        FType.POINT -> out += FixedPixelCircle(
                            pts[0], 7f * dens, osmPaint(col, 0f, Style.FILL), osmPaint(0xFFFFFFFF.toInt(), 2.5f * dens, Style.STROKE),
                        )
                        FType.AREA -> out += Polygon(
                            osmPaint((col and 0x00FFFFFF) or 0x44000000, 0f, Style.FILL), osmPaint(col, w, Style.STROKE), gf,
                        ).apply { setPoints(pts) }
                        else -> out += Polyline(osmPaint(col, w, Style.STROKE), gf).apply { setPoints(pts) }
                    }
                }
            }
            out.forEach { layers.add(it, false) }
            holder.featLayers = out
            holder.storeRef = store
            // titik GPS selalu di atas fitur
            holder.gpsAcc?.let { layers.remove(it, false) }
            holder.gpsDot?.let { layers.remove(it, false) }
            holder.gpsAcc = null
            holder.gpsDot = null
        }
        if (loc != null) {
            val ll = LatLong(loc.latitude, loc.longitude)
            val blue = 0xFF1A73E8.toInt()
            if (holder.gpsDot == null) {
                val acc = Circle(ll, loc.accuracy, osmPaint(0x241A73E8, 0f, Style.FILL), osmPaint(0x731A73E8, 1.5f * dens, Style.STROKE))
                val dot = FixedPixelCircle(ll, 7.5f * dens, osmPaint(blue, 0f, Style.FILL), osmPaint(0xFFFFFFFF.toInt(), 3f * dens, Style.STROKE))
                layers.add(acc, false)
                layers.add(dot, false)
                holder.gpsAcc = acc
                holder.gpsDot = dot
            } else {
                holder.gpsAcc?.setLatLong(ll)
                holder.gpsAcc?.setRadius(loc.accuracy)
                holder.gpsDot?.setLatLong(ll)
            }
            if (follow) mv.setCenter(ll)
        }
        mv.layerManager.redrawLayers()
    })
}
