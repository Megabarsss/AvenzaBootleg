# Catatan pembaruan

Setiap pembaruan dicatat di sini. Versi aplikasi tampil di dialog Proyek (ketuk nama proyek di bar atas).
Versi yang berjalan = `versionName` di `app/build.gradle.kts`.

## v0.9.1 (2026-11-12)
- Tombol pusatkan ala Google Maps di kanan bawah (di atas tombol +): ketuk untuk ke posisi GPS dan mengikuti; geser peta untuk berhenti mengikuti. Berlaku di peta jalan dan peta PDF.
- Peta jalan: titik posisi kini berukuran tetap dan kecil (tidak membesar saat zoom out), dengan kerucut arah hadap dan lingkaran akurasi.
- Peta jalan: titik fitur diperkecil; tanda silang tengah diperkecil.
- Perbaikan: teks bar koordinat tidak terbaca di tema gelap; pesan "Tap Peta untuk mengimpor PDF" tidak lagi muncul di peta jalan.

## v0.9 (2026-11-12)
- Peta jalan offline (OpenStreetMap, sampai jalan kecil): unduh dari dalam aplikasi lewat Peta > "Peta jalan offline (unduh)". Pilih per pulau (Jawa 880 MB, Sumatra 295 MB, Nusa Tenggara 175 MB, Sulawesi 171 MB, Kalimantan 160 MB, Papua 59 MB, Maluku 50 MB) atau seluruh Indonesia (1,7 GB). Unduhan berjalan di latar belakang dan bisa dilanjutkan.
- Di peta jalan: posisi GPS dengan lingkaran akurasi, mode ikuti posisi, semua fitur dari layer yang terlihat, dan tombol (+) untuk menaruh titik di tengah layar (tanda silang).
- Belum ada di peta jalan: alat ukur, kompas/rotasi, dan ketuk fitur untuk mengedit. Gunakan peta PDF/GeoTIFF untuk itu.
- Data peta © kontributor OpenStreetMap; file dari download.mapsforge.org.

## v0.8 (2026-11-12)
- Peta dasar Indonesia bawaan (offline, sudah di dalam APK): garis pantai, batas provinsi, dan kota utama. Buka lewat Peta > "Peta Indonesia". Peta ikhtisar (sekitar 1,7 km per piksel), bukan peta jalan detail. Sumber: Natural Earth (domain publik).
- Ikuti posisi: tombol posisi saya kini menyalakan/mematikan mode peta mengikuti GPS.
- Ke koordinat: Alat > "Ke koordinat…" untuk loncat ke lintang, bujur dengan penanda.
- Impor CSV titik (kolom lat/lon atau lintang/bujur; pemisah koma, titik koma, atau tab), juga di dalam ZIP.
- Ekspor KML/KMZ kini membawa warna fitur dan tebal garis.
- Layar tetap menyala selama aplikasi terbuka.

## v0.7 (2026-11-12)
- Antarmuka: ikon sungguhan menggantikan teks dan emoji (menu, layer, posisi saya).
- Menu bawah baru: ikon + label (Peta, Layer, Alat, Data).
- Tombol cepat (+) di kanan bawah untuk menambah titik dengan tap peta.

## v0.6 (2026-11-09)
- APK kini ditandatangani dengan kunci tetap, jadi pembaruan berikutnya bisa dipasang di atas versi lama tanpa uninstall dan data tidak hilang.
- PENTING: v0.6 memakai kunci baru, jadi pemasangan v0.6 pertama kali menggantikan v0.5 dan sebelumnya hanya setelah uninstall. Ekspor data penting dulu (Data > Ekspor). Setelah itu pembaruan aman.

## v0.5 (2026-11-09)
- Titik posisi GPS baru: titik biru dengan cincin putih dan bayangan, lingkaran akurasi sesuai ketelitian GPS, dan kerucut arah hadap (menggantikan garis lurus).

## v0.4 (2026-11-09)
- Impor ZIP berisi satu atau banyak file KML, KMZ, SHP, dan GPX (termasuk ZIP di dalam ZIP, dan subfolder). Tiap file menjadi satu layer bernama sesuai file. File yang gagal dilewati dan dilaporkan, yang lain tetap masuk.
- Menu Data: "Impor KML / KMZ / SHP / GPX / ZIP".

## v0.3.1 (2026-11-09)
- Teks tombol (menu bawah, bar atas, bar ukur, dialog proyek) selalu satu baris, tidak turun ke bawah.

## v0.3 (2026-11-09)
- Impor Shapefile: pilih file ZIP berisi .shp (+ .dbf untuk nama dan atribut). Titik, garis, dan poligon. Hanya WGS84 (EPSG:4326); shapefile berproyeksi ditolak dengan pesan.
- Edit fitur: warna per fitur, simbol titik (bulat, kotak, segitiga, belah ketupat, silang), tebal garis, pindah fitur ke layer lain.
- Pindahkan titik: tap peta atau ke posisi GPS saat ini.
- Ubah track/garis menjadi area, dan area menjadi garis.
- Layer: ubah warna dan nama layer.

## v0.2 (2026-11-09)
- Proyek terpisah: tiap proyek punya peta (PDF), layer, dan titik sendiri. Data lama masuk ke "Proyek 1".
- Tata letak baru terinspirasi Avenza, disesuaikan untuk layar HP 6,78": peta layar penuh, bar judul atas, kompas dan tombol posisi saya di kanan, bar koordinat GPS, menu bawah Peta / Layer / Alat / Data.
- Logo aplikasi "Pengendalian Pemanfaatan Ruang Laut" sebagai ikon dan di layar awal.
- Animasi halus pada panel bawah.
- Build CI kini menampilkan pesan error kompilasi sebagai anotasi.
- APK dinamai dengan versi (PdfMaps-v0.2.apk) dan tiap versi punya halaman Release sendiri.
- Perbaikan: bentrok nama `setMode` yang membuat build gagal (diganti `changeMode`).

## v0.1
- Versi awal: impor PDF/GeoPDF/GeoTIFF (termasuk BigTIFF), posisi GPS offline, titik/garis/area, rekam jejak, ekspor, rotasi peta dan kompas, penggaris dengan pilihan satuan, kunci rotasi.

Catatan: tata letak ini meniru gaya dan susunan umum Avenza Maps, bukan salinan persis.
