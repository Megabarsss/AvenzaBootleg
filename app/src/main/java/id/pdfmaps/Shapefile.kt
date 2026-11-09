package id.pdfmaps

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Ekspor ESRI Shapefile (WGS84) dalam satu ZIP: titik, garis, dan area dipisah jadi tiga set file. */
object Shapefile {
    private const val PRJ =
        "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]]," +
            "PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]"

    private class Field(val name: String, val width: Int, val source: (Feature) -> String)

    fun toZip(layers: List<Layer>, feats: List<Feature>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            val groups = listOf(
                Triple("points", 1, feats.filter { it.type == FType.POINT }),
                Triple("lines", 3, feats.filter { it.type == FType.LINE || it.type == FType.TRACK }),
                Triple("areas", 5, feats.filter { it.type == FType.AREA }),
            )
            for ((base, shapeType, fs) in groups) {
                if (fs.isEmpty()) continue
                val (shp, shx) = buildShp(shapeType, fs)
                fun put(ext: String, data: ByteArray) {
                    zip.putNextEntry(ZipEntry("$base.$ext"))
                    zip.write(data)
                    zip.closeEntry()
                }
                put("shp", shp)
                put("shx", shx)
                put("dbf", buildDbf(layers, fs))
                put("prj", PRJ.toByteArray())
                put("cpg", "UTF-8".toByteArray())
            }
        }
        return bos.toByteArray()
    }

    private fun ring(f: Feature): List<Pt> {
        var pts = f.pts
        if (f.type == FType.AREA) {
            if (pts.first().lat != pts.last().lat || pts.first().lon != pts.last().lon) pts = pts + pts.first()
            var a = 0.0
            for (i in 0 until pts.size - 1) a += pts[i].lon * pts[i + 1].lat - pts[i + 1].lon * pts[i].lat
            if (a > 0) pts = pts.reversed() // shapefile: cincin luar searah jarum jam
        }
        return pts
    }

    private fun buildShp(type: Int, feats: List<Feature>): Pair<ByteArray, ByteArray> {
        val contents = ArrayList<ByteArray>()
        var xmin = Double.MAX_VALUE; var ymin = Double.MAX_VALUE
        var xmax = -Double.MAX_VALUE; var ymax = -Double.MAX_VALUE
        for (f in feats) {
            val pts = if (type == 1) listOf(f.pts[0]) else ring(f)
            for (p in pts) {
                xmin = minOf(xmin, p.lon); xmax = maxOf(xmax, p.lon)
                ymin = minOf(ymin, p.lat); ymax = maxOf(ymax, p.lat)
            }
            if (type == 1) {
                val b = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
                b.putInt(1); b.putDouble(pts[0].lon); b.putDouble(pts[0].lat)
                contents.add(b.array())
            } else {
                val bx0 = pts.minOf { it.lon }; val bx1 = pts.maxOf { it.lon }
                val by0 = pts.minOf { it.lat }; val by1 = pts.maxOf { it.lat }
                val b = ByteBuffer.allocate(4 + 32 + 8 + 4 + 16 * pts.size).order(ByteOrder.LITTLE_ENDIAN)
                b.putInt(type)
                b.putDouble(bx0); b.putDouble(by0); b.putDouble(bx1); b.putDouble(by1)
                b.putInt(1); b.putInt(pts.size); b.putInt(0)
                for (p in pts) { b.putDouble(p.lon); b.putDouble(p.lat) }
                contents.add(b.array())
            }
        }
        val shpLen = 100 + contents.sumOf { 8 + it.size }
        val shxLen = 100 + 8 * contents.size
        fun header(total: Int): ByteBuffer {
            val h = ByteBuffer.allocate(100)
            h.order(ByteOrder.BIG_ENDIAN)
            h.putInt(9994)
            for (i in 0 until 5) h.putInt(0)
            h.putInt(total / 2)
            h.order(ByteOrder.LITTLE_ENDIAN)
            h.putInt(1000); h.putInt(type)
            h.putDouble(xmin); h.putDouble(ymin); h.putDouble(xmax); h.putDouble(ymax)
            for (i in 0 until 4) h.putDouble(0.0)
            return h
        }
        val shp = ByteBuffer.allocate(shpLen)
        shp.put(header(shpLen).array())
        val shx = ByteBuffer.allocate(shxLen)
        shx.put(header(shxLen).array())
        var offset = 100
        for ((i, c) in contents.withIndex()) {
            shp.order(ByteOrder.BIG_ENDIAN)
            shp.putInt(i + 1); shp.putInt(c.size / 2)
            shp.put(c)
            shx.order(ByteOrder.BIG_ENDIAN)
            shx.putInt(offset / 2); shx.putInt(c.size / 2)
            offset += 8 + c.size
        }
        return shp.array() to shx.array()
    }

    private fun fit(s: String, width: Int): ByteArray {
        var t = s
        var b = t.toByteArray(Charsets.UTF_8)
        while (b.size > width && t.isNotEmpty()) { t = t.dropLast(1); b = t.toByteArray(Charsets.UTF_8) }
        return b
    }

    private fun buildDbf(layers: List<Layer>, feats: List<Feature>): ByteArray {
        val fields = ArrayList<Field>()
        fields.add(Field("NAME", 80) { it.name })
        fields.add(Field("LAYER", 40) { f -> layers.firstOrNull { it.id == f.layerId }?.name ?: "" })
        fields.add(Field("NOTES", 200) { it.notes })
        fields.add(Field("TYPE", 10) { typeLabel(it.type) })
        val used = fields.map { it.name }.toMutableSet()
        for (attr in layers.flatMap { it.fields }.distinct().take(20)) {
            var n = attr.uppercase().map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }.joinToString("").take(10)
            if (n.isEmpty()) n = "ATTR"
            var k = 1
            val orig = n
            while (n in used) { val sfx = (k++).toString(); n = orig.take(10 - sfx.length) + sfx }
            used.add(n)
            fields.add(Field(n, 80) { it.attrs[attr] ?: "" })
        }
        val headerLen = 32 + 32 * fields.size + 1
        val recLen = 1 + fields.sumOf { it.width }
        val out = ByteBuffer.allocate(headerLen + recLen * feats.size + 1).order(ByteOrder.LITTLE_ENDIAN)
        val cal = java.util.Calendar.getInstance()
        out.put(0x03)
        out.put((cal.get(java.util.Calendar.YEAR) - 1900).toByte())
        out.put((cal.get(java.util.Calendar.MONTH) + 1).toByte())
        out.put(cal.get(java.util.Calendar.DAY_OF_MONTH).toByte())
        out.putInt(feats.size)
        out.putShort(headerLen.toShort())
        out.putShort(recLen.toShort())
        out.put(ByteArray(20))
        for (f in fields) {
            val nm = f.name.toByteArray(Charsets.US_ASCII)
            out.put(nm)
            out.put(ByteArray(11 - nm.size))
            out.put('C'.code.toByte())
            out.put(ByteArray(4))
            out.put(f.width.toByte())
            out.put(0)
            out.put(ByteArray(14))
        }
        out.put(0x0D)
        for (ft in feats) {
            out.put(' '.code.toByte())
            for (f in fields) {
                val b = fit(f.source(ft), f.width)
                out.put(b)
                for (i in b.size until f.width) out.put(' '.code.toByte())
            }
        }
        out.put(0x1A)
        return out.array()
    }
}

/** Pembaca Shapefile (.shp + .dbf opsional). Hanya koordinat WGS84 (lon/lat). */
object ShpReader {
    fun parse(shp: ByteArray, dbf: ByteArray?): List<RawFeature> {
        val b = ByteBuffer.wrap(shp)
        if (shp.size < 100) throw IllegalStateException("File .shp rusak")
        b.order(ByteOrder.BIG_ENDIAN)
        if (b.getInt(0) != 9994) throw IllegalStateException("Bukan file shapefile")
        val attrs = if (dbf != null) readDbf(dbf) else emptyList()
        val out = mutableListOf<RawFeature>()
        var pos = 100
        var idx = 0
        while (pos + 8 <= shp.size) {
            b.order(ByteOrder.BIG_ENDIAN)
            val lenBytes = b.getInt(pos + 4) * 2
            val start = pos + 8
            if (lenBytes < 4 || start + lenBytes > shp.size) break
            b.order(ByteOrder.LITTLE_ENDIAN)
            val type = b.getInt(start)
            val (nm, notes) = attrs.getOrNull(idx) ?: Pair("", "")
            val label = if (nm.isNotBlank()) nm else "Fitur ${idx + 1}"
            fun pt(off: Int) = Pt(b.getDouble(off + 8), b.getDouble(off))
            when (type) {
                1, 11, 21 -> out += RawFeature(label, notes, FType.POINT, listOf(pt(start + 4)))
                8, 18, 28 -> {
                    val n = b.getInt(start + 36)
                    for (i in 0 until n) out += RawFeature(label, notes, FType.POINT, listOf(pt(start + 40 + i * 16)))
                }
                3, 13, 23, 5, 15, 25 -> {
                    val area = type == 5 || type == 15 || type == 25
                    val nParts = b.getInt(start + 36)
                    val nPts = b.getInt(start + 40)
                    val partsOff = start + 44
                    val ptsOff = partsOff + nParts * 4
                    for (p in 0 until nParts) {
                        val a = b.getInt(partsOff + p * 4)
                        val e = if (p + 1 < nParts) b.getInt(partsOff + (p + 1) * 4) else nPts
                        val pts = (a until e).map { pt(ptsOff + it * 16) }.toMutableList()
                        if (area && pts.size > 1 && pts.first().lat == pts.last().lat && pts.first().lon == pts.last().lon) pts.removeAt(pts.size - 1)
                        val ok = if (area) pts.size >= 3 else pts.size >= 2
                        if (ok) out += RawFeature(label, notes, if (area) FType.AREA else FType.LINE, pts)
                    }
                }
                else -> {}
            }
            pos = start + lenBytes
            idx++
        }
        if (out.any { f -> f.pts.any { Math.abs(it.lon) > 180.0 || Math.abs(it.lat) > 90.0 } })
            throw IllegalStateException("Koordinat bukan WGS84 (lon/lat). Ubah dulu ke WGS84 (EPSG:4326), mis. di QGIS")
        return out
    }

    /** Daftar (nama, catatan) per rekaman dari file .dbf. */
    private fun readDbf(d: ByteArray): List<Pair<String, String>> {
        if (d.size < 33) return emptyList()
        val b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        val n = b.getInt(4)
        val hLen = b.getShort(8).toInt() and 0xFFFF
        val rLen = b.getShort(10).toInt() and 0xFFFF
        class Fld(val name: String, val off: Int, val len: Int)
        val flds = mutableListOf<Fld>()
        var p = 32
        var off = 1
        while (p + 32 <= hLen && d[p] != 0x0D.toByte()) {
            val nm = String(d, p, 11, Charsets.ISO_8859_1).trim { it == '\u0000' || it == ' ' }
            val len = d[p + 16].toInt() and 0xFF
            flds += Fld(nm, off, len)
            off += len
            p += 32
        }
        val nameIdx = flds.indexOfFirst { it.name.equals("name", true) || it.name.equals("nama", true) }
            .let { if (it >= 0) it else 0 }
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until n) {
            val rs = hLen + i * rLen
            if (rs + rLen > d.size) break
            val vals = flds.map { String(d, rs + it.off, it.len, Charsets.ISO_8859_1).trim() }
            val nm = vals.getOrNull(nameIdx) ?: ""
            val notes = flds.indices.filter { it != nameIdx && vals[it].isNotEmpty() }.joinToString("; ") { "${flds[it].name}=${vals[it]}" }
            out += Pair(nm, notes)
        }
        return out
    }
}
