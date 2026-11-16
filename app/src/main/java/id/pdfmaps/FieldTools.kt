package id.pdfmaps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cap koordinat/waktu pada foto. */
object PhotoStamp {
    /** Muat JPEG tegak (mengikuti orientasi EXIF), sisi terpanjang kira-kira <= maxDim. */
    fun loadUpright(f: File, maxDim: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxDim) s *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
            val deg = when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
        } catch (e: Throwable) {
            null
        }
    }

    /** Tulis baris teks pada pita gelap di bawah foto, simpan ke dst (boleh sama dengan src), dan isi EXIF GPS. */
    fun stamp(src: File, dst: File, lines: List<String>, lat: Double?, lon: Double?): Boolean {
        val base = loadUpright(src, 2400) ?: return false
        return try {
            val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
            val c = Canvas(bmp)
            val ts = maxOf(bmp.width, bmp.height) / 46f
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = ts; typeface = Typeface.DEFAULT_BOLD }
            val band = Paint().apply { color = 0xA6000000.toInt() }
            val pad = ts * 0.6f
            val h = lines.size * ts * 1.3f + pad * 1.5f
            c.drawRect(0f, bmp.height - h, bmp.width.toFloat(), bmp.height.toFloat(), band)
            var y = bmp.height - h + pad + ts * 0.85f
            for (ln in lines) {
                var t = ln
                while (t.length > 4 && text.measureText(t) > bmp.width - 2 * pad) t = t.dropLast(2)
                c.drawText(t, pad, y, text)
                y += ts * 1.3f
            }
            val tmp = File(dst.parentFile, dst.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            dst.delete()
            tmp.renameTo(dst)
            if (lat != null && lon != null) {
                try { ExifInterface(dst.path).apply { setLatLong(lat, lon); saveAttributes() } } catch (_: Throwable) {}
            }
            true
        } catch (e: Throwable) {
            false
        }
    }
}

/** Laporan lapangan PDF: ringkasan, peta ikhtisar, tabel fitur, dan foto. */
object Report {
    private const val PW = 595
    private const val PH = 842
    private const val M = 36f

    fun build(
        project: String,
        layers: List<Layer>,
        feats: List<Feature>,
        coordFormat: Int,
        mapBitmap: Bitmap?,
        toMap: ((Pt) -> Pair<Float, Float>)?,
        photosDir: File,
    ): ByteArray {
        val doc = PdfDocument()
        var pageNo = 0
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF0F6B57.toInt() }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF555555.toInt() }
        val rule = Paint().apply { color = 0xFFBBBBBB.toInt(); strokeWidth = 0.6f }
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = M

        fun finish() { page?.let { p -> c!!.drawText("PDF Maps · hal. $pageNo", PW - M - 70f, PH - 18f, small); doc.finishPage(p) } }
        fun newPage() {
            finish()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, pageNo).create())
            c = page!!.canvas
            y = M
        }
        fun clip(s: String, p: Paint, w: Float): String {
            if (p.measureText(s) <= w) return s
            var t = s
            while (t.length > 1 && p.measureText("$t…") > w) t = t.dropLast(1)
            return "$t…"
        }
        fun layerOf(f: Feature) = layers.firstOrNull { it.id == f.layerId }
        fun colorOf(f: Feature) = (if (f.color != 0L) f.color else (layerOf(f)?.color ?: 0xFF1E88E5)).toInt()

        // ---------- halaman 1: ringkasan + peta ----------
        newPage()
        c!!.drawText("Laporan Lapangan", M, y + 18f, title); y += 34f
        c!!.drawText("Proyek: $project", M, y, h2); y += 15f
        c!!.drawText("Dibuat: " + SimpleDateFormat("d MMMM yyyy HH:mm", Locale("id")).format(Date()), M, y, body); y += 13f
        val nPt = feats.count { it.type == FType.POINT }
        val lines = feats.filter { it.type == FType.LINE || it.type == FType.TRACK }
        val areas = feats.filter { it.type == FType.AREA }
        c!!.drawText(
            "Titik: $nPt · Garis/jejak: ${lines.size} (total ${GeoMath.fmtDist(lines.sumOf { GeoMath.length(it.pts) })}) · " +
                "Area: ${areas.size} (total ${GeoMath.fmtArea(areas.sumOf { GeoMath.areaM2(it.pts) })})",
            M, y, body,
        )
        y += 14f
        c!!.drawLine(M, y, PW - M, y, rule); y += 8f

        val boxW = PW - 2 * M
        val boxH = 400f
        val allPts = feats.flatMap { it.pts }
        if (allPts.isNotEmpty() || mapBitmap != null) {
            val cv = c!!
            cv.save()
            cv.clipRect(M, y, M + boxW, y + boxH)
            val proj: (Pt) -> Pair<Float, Float>
            if (mapBitmap != null && toMap != null) {
                val sc = minOf(boxW / mapBitmap.width, boxH / mapBitmap.height)
                val ox = M + (boxW - mapBitmap.width * sc) / 2f
                cv.drawBitmap(mapBitmap, null, RectF(ox, y, ox + mapBitmap.width * sc, y + mapBitmap.height * sc), Paint(Paint.FILTER_BITMAP_FLAG))
                val y0 = y
                proj = { p -> val q = toMap(p); Pair(ox + q.first * sc, y0 + q.second * sc) }
            } else {
                val la0 = allPts.minOf { it.lat }; val la1 = allPts.maxOf { it.lat }
                val lo0 = allPts.minOf { it.lon }; val lo1 = allPts.maxOf { it.lon }
                val k = Math.cos(Math.toRadians((la0 + la1) / 2))
                val w = maxOf((lo1 - lo0) * k, 1e-5); val h = maxOf(la1 - la0, 1e-5)
                val sc = minOf((boxW - 30f) / w, (boxH - 30f) / h).toFloat()
                val ox = M + (boxW - (w * sc).toFloat()) / 2f
                val oy = y + (boxH - (h * sc).toFloat()) / 2f
                cv.drawRect(M, y, M + boxW, y + boxH, Paint().apply { color = 0xFFF4F6F1.toInt() })
                proj = { p -> Pair(ox + ((p.lon - lo0) * k * sc).toFloat(), oy + ((la1 - p.lat) * sc).toFloat()) }
            }
            val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; typeface = Typeface.DEFAULT_BOLD }
            feats.forEachIndexed { i, f ->
                if (f.pts.isEmpty()) return@forEachIndexed
                val col = colorOf(f)
                val xy = f.pts.map(proj)
                if (f.type == FType.POINT) {
                    cv.drawCircle(xy[0].first, xy[0].second, 3.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
                    cv.drawCircle(xy[0].first, xy[0].second, 2.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
                } else {
                    val path = Path()
                    xy.forEachIndexed { j, q -> if (j == 0) path.moveTo(q.first, q.second) else path.lineTo(q.first, q.second) }
                    if (f.type == FType.AREA) {
                        path.close()
                        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (col and 0x00FFFFFF) or 0x44000000; style = Paint.Style.FILL })
                    }
                    cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.STROKE; strokeWidth = 1.4f })
                }
                val lx = if (f.type == FType.POINT) xy[0].first else xy.map { it.first }.average().toFloat()
                val ly = if (f.type == FType.POINT) xy[0].second else xy.map { it.second }.average().toFloat()
                cv.drawText("${i + 1}", lx + 4f, ly - 3f, num.apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f })
                cv.drawText("${i + 1}", lx + 4f, ly - 3f, num.apply { color = 0xFF111111.toInt(); style = Paint.Style.FILL })
            }
            cv.restore()
            cv.drawRect(M, y, M + boxW, y + boxH, Paint().apply { style = Paint.Style.STROKE; color = 0xFF999999.toInt(); strokeWidth = 0.6f })
            y += boxH + 10f
            cv.drawText("Nomor pada peta mengacu ke kolom No di tabel fitur.", M, y, small); y += 16f
        }

        // ---------- tabel fitur ----------
        val colX = floatArrayOf(M, M + 24f, M + 160f, M + 205f, M + 290f)
        fun header() {
            val cv = c!!
            listOf("No", "Nama", "Jenis", "Layer", "Koordinat / ukuran · catatan").forEachIndexed { i, t -> cv.drawText(t, colX[i], y, h2.apply { textSize = 9f }) }
            y += 5f; cv.drawLine(M, y, PW - M, y, rule); y += 11f
        }
        if (y > PH - 140f) newPage()
        c!!.drawText("Daftar fitur (${feats.size})", M, y + 4f, h2.apply { textSize = 12f }); y += 20f
        header()
        feats.forEachIndexed { i, f ->
            if (y > PH - 60f) { newPage(); header() }
            val cv = c!!
            val info = when (f.type) {
                FType.POINT -> GeoMath.fmtCoord(f.pts[0].lat, f.pts[0].lon, coordFormat) + (if (!f.pts[0].ele.isNaN()) " · %.0f m dpl".format(f.pts[0].ele) else "")
                FType.AREA -> "Luas ${GeoMath.fmtArea(GeoMath.areaM2(f.pts))} · keliling ${GeoMath.fmtDist(GeoMath.perimeter(f.pts))}"
                else -> "Panjang ${GeoMath.fmtDist(GeoMath.length(f.pts))} · ${f.pts.size} titik"
            }
            cv.drawText("${i + 1}", colX[0], y, body)
            cv.drawText(clip(f.name, body, 130f), colX[1], y, body)
            cv.drawText(typeLabel(f.type), colX[2], y, body)
            cv.drawText(clip(layerOf(f)?.name ?: "-", body, 80f), colX[3], y, body)
            cv.drawText(clip(info, body, PW - M - colX[4]), colX[4], y, body)
            val extra = (listOf(f.notes) + f.attrs.filter { it.value.isNotBlank() }.map { "${it.key}: ${it.value}" }).filter { it.isNotBlank() }.joinToString(" · ")
            if (extra.isNotBlank()) { y += 10f; cv.drawText(clip(extra.replace("\n", " "), small, PW - M - colX[4]), colX[4], y, small) }
            y += 5f; cv.drawLine(M, y, PW - M, y, rule.apply { color = 0xFFE2E2E2.toInt() }); y += 11f
        }

        // ---------- foto ----------
        val withPhoto = feats.withIndex().filter { it.value.photo != null && File(photosDir, it.value.photo!!).exists() }
        var slot = 0
        for ((i, f) in withPhoto) {
            if (slot % 2 == 0) { newPage(); c!!.drawText("Foto", M, y + 4f, h2.apply { textSize = 12f }); y += 18f }
            val bmp = PhotoStamp.loadUpright(File(photosDir, f.photo!!), 1100)
            if (bmp != null) {
                val maxW = PW - 2 * M; val maxH = 330f
                val sc = minOf(maxW / bmp.width, maxH / bmp.height)
                val w = bmp.width * sc; val h = bmp.height * sc
                val x = M + (maxW - w) / 2f
                c!!.drawBitmap(bmp, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                y += h + 12f
                c!!.drawText(clip("${i + 1}. ${f.name} · " + GeoMath.fmtCoord(f.pts[0].lat, f.pts[0].lon, coordFormat), body, maxW), M, y, body)
                y += 22f
                bmp.recycle()
            }
            slot++
        }
        finish()
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
