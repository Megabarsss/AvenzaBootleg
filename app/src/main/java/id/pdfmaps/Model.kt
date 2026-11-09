package id.pdfmaps

import java.util.UUID

/** Satu titik koordinat. ele = elevasi (NaN jika tidak ada), t = waktu epoch ms (0 jika tidak ada). */
data class Pt(val lat: Double, val lon: Double, val ele: Double = Double.NaN, val t: Long = 0L)

enum class FType { POINT, LINE, TRACK, AREA }

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val visible: Boolean = true,
    val color: Long = 0xFFFF9800,
    val fields: List<String> = emptyList(),   // nama atribut kustom untuk fitur di layer ini
)

data class Feature(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String,
    val type: FType,
    val name: String,
    val notes: String = "",
    val pts: List<Pt>,
    val photo: String? = null,   // nama file di filesDir/photos
    val radius: Double = 0.0,    // geofence dalam meter (hanya POINT), 0 = mati
    val attrs: Map<String, String> = emptyMap(),
    val created: Long = System.currentTimeMillis(),
    val color: Long = 0L,        // 0 = ikut warna layer
    val symbol: Int = 0,         // bentuk titik: 0 bulat, 1 kotak, 2 segitiga, 3 belah ketupat, 4 silang
    val width: Int = 0,          // tebal garis: 0 standar, 1 tipis, 2 sedang, 3 tebal
)

data class MapEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val file: String,                       // nama file di filesDir/maps
    val isImage: Boolean,
    val manual: Map<Int, List<ControlPoint>> = emptyMap(), // titik kalibrasi manual per halaman
)

data class Store(
    val maps: List<MapEntry> = emptyList(),
    val layers: List<Layer> = emptyList(),
    val features: List<Feature> = emptyList(),
    val activeLayerId: String? = null,
    val coordFormat: Int = 0,   // 0 = desimal, 1 = DMS, 2 = UTM, 3 = MGRS
    val lastMapId: String? = null,
    val bgGeofence: Boolean = false, // pantau geofence di latar belakang
    val unit: Int = 0, // satuan ukur (lihat GeoMath.UNIT_NAMES)
) {
    fun ensured(): Store {
        if (layers.isNotEmpty()) return this
        val l = Layer(name = "Layer 1")
        return copy(layers = listOf(l), activeLayerId = l.id)
    }
}

val LAYER_COLORS = longArrayOf(
    0xFFFF9800, 0xFF2196F3, 0xFF4CAF50, 0xFF9C27B0, 0xFFE91E63, 0xFF795548, 0xFF00BCD4,
)

fun typeLabel(t: FType) = when (t) {
    FType.POINT -> "titik"
    FType.LINE -> "garis"
    FType.TRACK -> "track"
    FType.AREA -> "area"
}

data class Project(val id: String, val name: String)

class ProjectIndex(val active: String, val projects: List<Project>)

val FEATURE_COLORS = longArrayOf(
    0xFFE53935, 0xFFFF9800, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5,
    0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41, 0xFF212121, 0xFF757575, 0xFFFFFFFF,
)
val SYMBOL_NAMES = listOf("Bulat", "Kotak", "Segitiga", "Belah ketupat", "Silang")
val WIDTH_NAMES = listOf("Standar", "Tipis", "Sedang", "Tebal")
