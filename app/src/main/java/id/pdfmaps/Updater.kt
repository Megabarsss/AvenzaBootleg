package id.pdfmaps

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Pembaruan dari dalam aplikasi: cek rilis terbaru di GitHub, unduh APK, lalu buka pemasang. */
object Updater {
    class Info(val version: String, val url: String)

    private const val API = "https://api.github.com/repos/Megabarsss/AvenzaBootleg/releases/tags/latest"

    private fun parts(v: String) = v.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }

    /** true bila versi a lebih baru daripada b (mis. "0.13" > "0.9.1"). */
    fun newer(a: String, b: String): Boolean {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    /** Rilis terbaru (versi dan URL APK), atau null bila gagal dibaca. Panggil di luar thread utama. */
    fun latest(): Info? {
        val c = URL(API).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "PdfMaps-Android")
            if (c.responseCode != 200) return null
            val assets = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val m = Regex("^PdfMaps-v([0-9.]+)\\.apk$").find(a.optString("name")) ?: continue
                return Info(m.groupValues[1], a.optString("browser_download_url"))
            }
            null
        } finally {
            c.disconnect()
        }
    }

    fun file(ctx: Context, info: Info) = File(File(ctx.getExternalFilesDir(null), "update").apply { mkdirs() }, "PdfMaps-v${info.version}.apk")

    fun enqueue(ctx: Context, info: Info): Long {
        File(ctx.getExternalFilesDir(null), "update").listFiles()?.forEach { it.delete() }
        val req = DownloadManager.Request(Uri.parse(info.url))
            .setTitle("PDF Maps v${info.version}")
            .setDescription("Mengunduh pembaruan")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(ctx, null, "update/PdfMaps-v${info.version}.apk")
        return (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
    }

    /** Persen 0..99 saat berjalan, 100 bila selesai, -1 bila gagal. */
    fun progress(ctx: Context, id: Long): Int {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (c == null || !c.moveToFirst()) return -1
            val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (st) {
                DownloadManager.STATUS_SUCCESSFUL -> 100
                DownloadManager.STATUS_FAILED -> -1
                else -> if (total > 0) (done * 99 / total).toInt() else 0
            }
        }
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "id.pdfmaps.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
