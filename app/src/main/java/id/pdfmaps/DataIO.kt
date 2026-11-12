package id.pdfmaps

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

/** Hasil baca file KML/GPX sebelum dimasukkan ke layer. */
class RawFeature(val name: String, val notes: String, val type: FType, val pts: List<Pt>)

object Exporter {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun iso(t: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(t))
    }

    fun toGpx(layers: List<Layer>, feats: List<Feature>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"PdfMaps\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        for (f in feats) {
            val desc = if (f.notes.isBlank()) "" else "<desc>${esc(f.notes)}</desc>"
            when (f.type) {
                FType.POINT -> {
                    val p = f.pts[0]
                    append("  <wpt lat=\"${p.lat}\" lon=\"${p.lon}\">")
                    if (!p.ele.isNaN()) append("<ele>${p.ele}</ele>")
                    append("<name>${esc(f.name)}</name>$desc</wpt>\n")
                }
                FType.LINE -> {
                    append("  <rte><name>${esc(f.name)}</name>$desc\n")
                    f.pts.forEach { append("    <rtept lat=\"${it.lat}\" lon=\"${it.lon}\"/>\n") }
                    append("  </rte>\n")
                }
                FType.TRACK, FType.AREA -> {
                    val pts = if (f.type == FType.AREA) f.pts + f.pts.first() else f.pts
                    append("  <trk><name>${esc(f.name)}</name>$desc<trkseg>\n")
                    for (p in pts) {
                        append("    <trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">")
                        if (!p.ele.isNaN()) append("<ele>${p.ele}</ele>")
                        if (p.t > 0) append("<time>${iso(p.t)}</time>")
                        append("</trkpt>\n")
                    }
                    append("  </trkseg></trk>\n")
                }
            }
        }
        append("</gpx>\n")
    }

    /** ARGB (0xAARRGGBB) -> warna KML "aabbggrr". */
    fun kmlColor(c: Long): String {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return "ff%02x%02x%02x".format(b, g, r)
    }

    fun toKml(layers: List<Layer>, feats: List<Feature>): String = buildString {
        fun coords(pts: List<Pt>) =
            pts.joinToString(" ") { "${it.lon},${it.lat},${if (it.ele.isNaN()) 0 else it.ele}" }
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n")
        for (l in layers) {
            val fs = feats.filter { it.layerId == l.id }
            if (fs.isEmpty()) continue
            append("<Folder><name>${esc(l.name)}</name>\n")
            for (f in fs) {
                append("<Placemark><name>${esc(f.name)}</name>")
                if (f.notes.isNotBlank()) append("<description>${esc(f.notes)}</description>")
                val kc = kmlColor(if (f.color != 0L) f.color else l.color)
                val lw = when (f.width) { 1 -> 2; 2 -> 4; 3 -> 8; else -> 3 }
                append("<Style><IconStyle><color>$kc</color></IconStyle><LineStyle><color>$kc</color><width>$lw</width></LineStyle>")
                append("<PolyStyle><color>66${kc.substring(2)}</color></PolyStyle></Style>")
                if (f.attrs.isNotEmpty()) {
                    append("<ExtendedData>")
                    f.attrs.forEach { (k, v) -> append("<Data name=\"${esc(k)}\"><value>${esc(v)}</value></Data>") }
                    append("</ExtendedData>")
                }
                when (f.type) {
                    FType.POINT -> append("<Point><coordinates>${coords(f.pts)}</coordinates></Point>")
                    FType.LINE, FType.TRACK ->
                        append("<LineString><coordinates>${coords(f.pts)}</coordinates></LineString>")
                    FType.AREA -> append(
                        "<Polygon><outerBoundaryIs><LinearRing><coordinates>${coords(f.pts + f.pts.first())}" +
                            "</coordinates></LinearRing></outerBoundaryIs></Polygon>"
                    )
                }
                append("</Placemark>\n")
            }
            append("</Folder>\n")
        }
        append("</Document></kml>\n")
    }

    fun toKmz(layers: List<Layer>, feats: List<Feature>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("doc.kml"))
            z.write(toKml(layers, feats).toByteArray())
            z.closeEntry()
        }
        return bos.toByteArray()
    }

    fun toCsv(layers: List<Layer>, feats: List<Feature>): String = buildString {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val attrCols = layers.flatMap { it.fields }.distinct()
        append("name,type,layer,lat,lon,elevation_m,notes,length_m,area_m2")
        attrCols.forEach { append(",").append(q(it)) }
        append("\n")
        for (f in feats) {
            val layer = layers.firstOrNull { it.id == f.layerId }?.name ?: ""
            val c = if (f.type == FType.POINT) f.pts[0] else GeoMath.centroid(f.pts)
            val len = when (f.type) {
                FType.LINE, FType.TRACK -> GeoMath.length(f.pts)
                FType.AREA -> GeoMath.perimeter(f.pts)
                else -> 0.0
            }
            val area = if (f.type == FType.AREA) GeoMath.areaM2(f.pts) else 0.0
            append("${q(f.name)},${typeLabel(f.type)},${q(layer)},${c.lat},${c.lon},")
            append("${if (f.pts[0].ele.isNaN()) "" else f.pts[0].ele.toString()},${q(f.notes)},$len,$area")
            attrCols.forEach { append(",").append(q(f.attrs[it] ?: "")) }
            append("\n")
        }
    }
}

object Importer {
    private fun parseCoords(s: String): List<Pt> =
        s.trim().split(Regex("\\s+")).mapNotNull {
            val c = it.split(",")
            if (c.size < 2) null else {
                val lon = c[0].toDoubleOrNull()
                val lat = c[1].toDoubleOrNull()
                if (lat == null || lon == null) null
                else Pt(lat, lon, c.getOrNull(2)?.toDoubleOrNull() ?: Double.NaN)
            }
        }

    fun parseKml(input: InputStream): List<RawFeature> {
        val p = Xml.newPullParser()
        p.setInput(input, null)
        val out = mutableListOf<RawFeature>()
        var inPm = false
        var name = ""
        var desc = ""
        var type: FType? = null
        var pts = emptyList<Pt>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "Placemark" -> { inPm = true; name = ""; desc = ""; type = null; pts = emptyList() }
                    "name" -> if (inPm) name = p.nextText()
                    "description" -> if (inPm) desc = p.nextText()
                    "Point" -> if (inPm && type == null) type = FType.POINT
                    "LineString" -> if (inPm && type == null) type = FType.LINE
                    "Polygon" -> if (inPm && type == null) type = FType.AREA
                    "coordinates" -> {
                        val txt = p.nextText()
                        if (inPm && pts.isEmpty()) pts = parseCoords(txt)
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "Placemark") {
                inPm = false
                val t = type
                if (t != null && pts.isNotEmpty()) {
                    var use = pts
                    if (t == FType.AREA && use.size > 1 && use.first().lat == use.last().lat &&
                        use.first().lon == use.last().lon
                    ) use = use.dropLast(1)
                    val ok = when (t) {
                        FType.POINT -> use.isNotEmpty()
                        FType.LINE -> use.size >= 2
                        else -> use.size >= 3
                    }
                    if (ok) out.add(RawFeature(name.ifBlank { "Tanpa nama" }, desc, t, if (t == FType.POINT) listOf(use[0]) else use))
                }
            }
            ev = p.next()
        }
        return out
    }

    fun parseGpx(input: InputStream): List<RawFeature> {
        val p = Xml.newPullParser()
        p.setInput(input, null)
        val out = mutableListOf<RawFeature>()
        var ctx: String? = null
        var name = ""
        var notes = ""
        var pts = mutableListOf<Pt>()
        var inPt = false
        var lat = 0.0
        var lon = 0.0
        var ele = Double.NaN
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "wpt", "trk", "rte" -> {
                        ctx = p.name; name = ""; notes = ""; pts = mutableListOf()
                        if (p.name == "wpt") {
                            lat = p.getAttributeValue(null, "lat").toDouble()
                            lon = p.getAttributeValue(null, "lon").toDouble()
                            ele = Double.NaN
                            inPt = true
                        }
                    }
                    "trkpt", "rtept" -> {
                        lat = p.getAttributeValue(null, "lat").toDouble()
                        lon = p.getAttributeValue(null, "lon").toDouble()
                        ele = Double.NaN
                        inPt = true
                    }
                    "name" -> if (ctx != null && (!inPt || ctx == "wpt")) name = p.nextText()
                    "desc" -> if (ctx != null && (!inPt || ctx == "wpt")) notes = p.nextText()
                    "ele" -> if (inPt) ele = p.nextText().trim().toDoubleOrNull() ?: Double.NaN
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (p.name) {
                    "trkpt", "rtept" -> { pts.add(Pt(lat, lon, ele)); inPt = false }
                    "wpt" -> {
                        out.add(RawFeature(name.ifBlank { "Titik" }, notes, FType.POINT, listOf(Pt(lat, lon, ele))))
                        ctx = null; inPt = false
                    }
                    "trk" -> {
                        if (pts.size >= 2) out.add(RawFeature(name.ifBlank { "Track" }, notes, FType.TRACK, pts.toList()))
                        ctx = null
                    }
                    "rte" -> {
                        if (pts.size >= 2) out.add(RawFeature(name.ifBlank { "Rute" }, notes, FType.LINE, pts.toList()))
                        ctx = null
                    }
                }
            }
            ev = p.next()
        }
        return out
    }
}

/** Impor satu file atau ZIP berisi banyak KML / KMZ / SHP / GPX (juga ZIP di dalam ZIP). Satu file = satu layer. */
object BundleImport {
    class Result {
        val layers = mutableListOf<Pair<String, List<RawFeature>>>()
        val warnings = mutableListOf<String>()
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()
    private fun isShp(b: ByteArray) = b.size > 100 && b[0] == 0.toByte() && b[1] == 0.toByte() && b[2] == 0x27.toByte() && b[3] == 0x0A.toByte()
    private fun baseOf(name: String) = name.substringAfterLast('/').substringBeforeLast('.')
    private fun stem(path: String) = path.substringBeforeLast('.')

    fun parse(name: String, bytes: ByteArray, container: String, depth: Int, res: Result) {
        val base = baseOf(name).let { if (it.equals("doc", true) || it.isBlank()) container else it }
        try {
            when {
                isZip(bytes) && depth < 3 -> parseZip(name, bytes, base, depth, res)
                isShp(bytes) -> addShp(base, bytes, null, null, res)
                name.endsWith(".csv", true) || name.endsWith(".txt", true) -> {
                    val raws = CsvPoints.parse(bytes.decodeToString())
                    if (raws.isNotEmpty()) res.layers += Pair(base, raws) else res.warnings += "$base: kolom lat/lon tidak ditemukan"
                }
                else -> {
                    val head = bytes.decodeToString(0, minOf(bytes.size, 2000))
                    val raws = if (head.contains("<gpx")) Importer.parseGpx(java.io.ByteArrayInputStream(bytes))
                    else Importer.parseKml(java.io.ByteArrayInputStream(bytes))
                    if (raws.isNotEmpty()) res.layers += Pair(base, raws) else res.warnings += "$base: tidak ada fitur"
                }
            }
        } catch (ex: Exception) {
            res.warnings += "$base: ${ex.message}"
        }
    }

    private fun addShp(base: String, shp: ByteArray, dbf: ByteArray?, prj: ByteArray?, res: Result) {
        if (prj != null && prj.decodeToString().contains("PROJCS")) {
            res.warnings += "$base: shapefile berproyeksi, ubah dulu ke WGS84"
            return
        }
        val raws = ShpReader.parse(shp, dbf)
        if (raws.isNotEmpty()) res.layers += Pair(base, raws) else res.warnings += "$base: tidak ada fitur"
    }

    private fun parseZip(name: String, bytes: ByteArray, base: String, depth: Int, res: Result) {
        val entries = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { z ->
            var en = z.nextEntry
            var n = 0
            while (en != null && n < 1000) {
                if (!en.isDirectory && !en.name.startsWith("__MACOSX") && !en.name.substringAfterLast('/').startsWith("._")) {
                    entries[en.name] = z.readBytes(); n++
                }
                en = z.nextEntry
            }
        }
        val isKmz = name.endsWith(".kmz", true)
        if (isKmz) {
            val k = entries.entries.firstOrNull { it.key.equals("doc.kml", true) } ?: entries.entries.firstOrNull { it.key.endsWith(".kml", true) }
            if (k == null) { res.warnings += "$base: KMZ tanpa file .kml"; return }
            parse(k.key, k.value, base, depth + 1, res)
            return
        }
        for ((path, data) in entries) {
            val l = path.lowercase()
            when {
                l.endsWith(".kml") || l.endsWith(".gpx") || l.endsWith(".kmz") || l.endsWith(".zip") || l.endsWith(".csv") -> parse(path, data, base, depth + 1, res)
                l.endsWith(".shp") -> {
                    val s = stem(path).lowercase()
                    val dbf = entries.entries.firstOrNull { it.key.lowercase() == "$s.dbf" }?.value
                    val prj = entries.entries.firstOrNull { it.key.lowercase() == "$s.prj" }?.value
                    val lname = baseOf(path).let { if (it.equals("doc", true) || it.isBlank()) base else it }
                    try { addShp(lname, data, dbf, prj, res) } catch (ex: Exception) { res.warnings += "$lname: ${ex.message}" }
                }
            }
        }
    }
}

/** Impor titik dari CSV: kolom lat/lon (atau y/x, lintang/bujur), nama opsional; kolom lain masuk catatan. */
object CsvPoints {
    private fun split(line: String, sep: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') { if (q && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else q = !q }
            else if (ch == sep && !q) { out += sb.toString().trim(); sb.setLength(0) }
            else sb.append(ch)
            i++
        }
        out += sb.toString().trim()
        return out
    }

    fun parse(text: String): List<RawFeature> {
        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val sep = listOf(';', '\t', ',').maxByOrNull { c -> lines[0].count { it == c } } ?: ','
        val head = split(lines[0], sep).map { it.lowercase() }
        fun find(vararg names: String) = head.indexOfFirst { h -> names.any { h == it } }
        val iLat = find("lat", "latitude", "lintang", "y")
        val iLon = find("lon", "lng", "long", "longitude", "bujur", "x")
        if (iLat < 0 || iLon < 0) return emptyList()
        val iName = find("name", "nama", "label", "id", "kode")
        val iEle = find("ele", "elev", "elevation", "alt", "z", "tinggi")
        fun num(s: String) = (if (sep != ',') s.replace(',', '.') else s).toDoubleOrNull()
        val out = ArrayList<RawFeature>()
        for (k in 1 until lines.size) {
            val c = split(lines[k], sep)
            val lat = c.getOrNull(iLat)?.let { num(it) } ?: continue
            val lon = c.getOrNull(iLon)?.let { num(it) } ?: continue
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180) continue
            val nm = c.getOrNull(iName)?.takeIf { it.isNotBlank() } ?: "Titik $k"
            val notes = head.indices.filter { it != iLat && it != iLon && it != iName && it != iEle && !c.getOrNull(it).isNullOrBlank() }
                .joinToString("; ") { "${head[it]}=${c[it]}" }
            out += RawFeature(nm, notes, FType.POINT, listOf(Pt(lat, lon, c.getOrNull(iEle)?.let { num(it) } ?: Double.NaN)))
        }
        return out
    }
}
