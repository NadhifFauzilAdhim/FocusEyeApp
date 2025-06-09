# FocusEye

**FocusEye** adalah aplikasi Android berbasis YOLOv11, MediaPipe, dan TensorFlow Lite yang dirancang untuk memantau tingkat fokus siswa di dalam kelas secara real-time menggunakan kamera perangkat. Aplikasi ini cocok digunakan untuk penelitian, observasi perilaku belajar, maupun pengembangan sistem pembelajaran pintar.

---

## 🚀 Fitur Utama

- 🔍 **Deteksi Wajah & Fokus Visual** menggunakan model YOLOv11 (TFLite) dan MediaPipe Face Mesh.
- 👁️ **Analisis Arah Pandang (Gaze Direction)** dengan MediaPipe untuk mendeteksi apakah siswa melihat ke depan, samping, atau tidak fokus.
- 📱 **Deteksi Penggunaan Ponsel** dengan YOLOv11 untuk mengidentifikasi apakah siswa memegang atau melihat ke ponsel saat sesi berlangsung.
- 🚨 **Peringatan Otomatis (Alarm)** saat sistem mendeteksi siswa tidak fokus atau menggunakan ponsel dalam waktu tertentu.
- 📷 **Kamera Real-Time** dengan dukungan CameraX.
- 📊 **Area Deteksi Dinamis**: pengguna bisa menentukan posisi “board” di layar sebagai titik fokus utama.
- 🎨 **Antarmuka Modern** dengan transisi, splash screen, dan UI responsif.
- 👋 **Welcome Screen** yang muncul setiap kali aplikasi dibuka.

---

## 🧠 Teknologi yang Digunakan

| Teknologi           | Deskripsi                                              |
|---------------------|---------------------------------------------------------|
| YOLOv11 (TFLite)     | Model deteksi wajah & objek seperti ponsel              |
| MediaPipe Face Mesh | Deteksi landmark wajah untuk arah pandang               |
| TensorFlow Lite     | Inferensi model ML ringan di Android                    |
| Kotlin + XML        | Bahasa dan layout utama Android                         |
| CameraX             | Library kamera modern Android                           |
| ViewBinding         | Binding elemen layout tanpa `findViewById`              |
| Android AudioManager| Digunakan untuk mengaktifkan alarm atau notifikasi suara|

---

## 👁️ Analisis Arah Pandang (Gaze Estimation)

Arah pandang ditentukan menggunakan **MediaPipe Face Mesh**:

- Mengekstrak koordinat landmark mata kiri dan kanan (mis. 33, 133 untuk mata kanan, 362, 263 untuk mata kiri).
- Membandingkan posisi pupil relatif terhadap batas kelopak mata.
- Menentukan apakah subjek melihat ke:
  - ▶️ Kanan
  - ◀️ Kiri
  - 🔼 Atas
  - 🔽 Bawah
  - ⏺️ Tengah (fokus)

Ini digunakan untuk memperkirakan apakah siswa memperhatikan papan atau tidak.

---

## 📱 Deteksi Penggunaan Ponsel

Menggunakan **YOLOv11** yang telah dilatih untuk mendeteksi objek "handphone":

- Jika ponsel terdeteksi dalam frame kamera, status siswa akan dianggap **tidak fokus**.
- Sistem akan memicu peringatan jika penggunaan ponsel berlangsung lebih dari ambang batas waktu tertentu.

---

## 🚨 Fitur Alarm Otomatis

Aplikasi akan memberikan peringatan ketika:
- 👀 Siswa tidak melihat ke arah papan selama beberapa detik.
- 📱 Siswa terdeteksi menggunakan ponsel.
- ⚠️ Alarm berbentuk **suara**, tergantung pengaturan perangkat.
---