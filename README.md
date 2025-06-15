# 👁️ FocusEye

**FocusEye** adalah aplikasi Android inovatif yang berfungsi sebagai sistem pemantau fokus dan perhatian secara real-time. Dengan memanfaatkan kekuatan **YOLO**, **MediaPipe**, dan **TensorFlow Lite**, aplikasi ini dirancang untuk membantu pendidik, peneliti, dan orang tua dalam memahami tingkat konsentrasi siswa di lingkungan belajar.

Aplikasi ini mampu mendeteksi arah pandangan mata, penggunaan ponsel, dan memberikan peringatan otomatis — menjadikannya alat ideal untuk riset perilaku belajar dan pengembangan **kelas pintar**.

---

## 🚀 Fitur Unggulan

- **🎯 Deteksi Fokus Multi-Metode**  
  Menganalisis arah pandangan mata (gaze direction) menggunakan MediaPipe dan mendeteksi penggunaan ponsel dengan model YOLO untuk mendapatkan gambaran konsentrasi yang komprehensif.

- **🧍 Analisis Wajah Real-Time**  
  Menggunakan MediaPipe Face Mesh untuk melacak 478 landmark wajah secara akurat, memungkinkan estimasi arah pandang (atas, bawah, kiri, kanan, tengah) dengan presisi tinggi.

- **🔔 Peringatan Cerdas**  
  Sistem alarm otomatis aktif jika siswa terdeteksi tidak fokus atau menggunakan ponsel melebihi ambang batas waktu tertentu.

- **🖼️ Area Fokus Dinamis**  
  Pengguna dapat mendefinisikan area "papan tulis" atau titik fokus utama dalam frame kamera untuk analisis kontekstual.

- **🎨 Antarmuka Modern**  
  Dibangun dengan CameraX, ViewBinding, dan Material Design, memberikan pengalaman pengguna yang intuitif dan responsif.

---

## 🧠 Bagaimana Cara Kerjanya?

**FocusEye** mengintegrasikan beberapa teknologi machine learning untuk mencapai tujuannya:

1. **📱 Deteksi Objek (Ponsel):**  
   Menggunakan model YOLO (TensorFlow Lite) yang ringan untuk mendeteksi keberadaan ponsel di tangan atau sekitar siswa.

2. **👁️ Analisis Arah Pandang:**  
   MediaPipe Face Mesh memetakan 478 titik wajah. Dengan menghitung posisi pupil terhadap landmark mata, sistem dapat memperkirakan ke mana siswa melihat.

3. **🧩 Logika Keputusan:**  
   Data dari deteksi objek dan arah pandang digabungkan. Jika siswa melihat keluar area fokus atau terdeteksi menggunakan ponsel terlalu lama, status akan dianggap **"tidak fokus"** dan alarm dipicu.

---

## 🛠️ Tech Stack

| Komponen               | Teknologi yang Digunakan                  |
|------------------------|------------------------------------------|
| Deteksi Objek          | YOLOv11 via TensorFlow Lite              |
| Deteksi Wajah & Pandangan | Google MediaPipe (Face Mesh)          |
| ML Inference Engine    | TensorFlow Lite                          |
| Bahasa & Arsitektur    | Kotlin (Utama), XML (Layout)             |
| Kamera & UI            | CameraX, ViewBinding, Material Design    |
| Notifikasi Suara       | Android AudioManager                     |

---

## 🌱 Dibangun di Atas Fondasi Open Source

Komponen deteksi objek dalam aplikasi ini dikembangkan dari proyek [Object Detection Android App](https://github.com/AarohiSingla/Object-Detection-Android-App) oleh **Aarohi Singla**.

Kami mengembangkan lebih lanjut proyek tersebut dengan:
- Integrasi analisis arah pandang menggunakan MediaPipe
- Implementasi logika spesifik untuk kasus penggunaan pemantauan fokus siswa

Kami berterima kasih kepada komunitas open source yang memungkinkan proyek ini terwujud. ❤️

---
