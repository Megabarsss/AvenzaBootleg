# Catatan pembaruan

Setiap pembaruan dicatat di sini. Versi aplikasi tampil di dialog Proyek (ketuk nama proyek di bar atas).
Versi yang berjalan = `versionName` di `app/build.gradle.kts`.

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
