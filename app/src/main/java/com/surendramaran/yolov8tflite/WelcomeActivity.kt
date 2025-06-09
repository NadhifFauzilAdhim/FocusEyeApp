package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tampilkan layout activity_welcome.xml setiap kali aplikasi dibuka
        setupWelcomeScreen()
    }

    private fun setupWelcomeScreen() {
        // Inflate layout menggunakan ViewBinding
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ketika tombol "Lanjutkan" ditekan, arahkan ke MainActivity
        binding.buttonContinue.setOnClickListener {
            redirectToMain()
        }
    }

    private fun redirectToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Tutup WelcomeActivity agar tidak bisa dikembalikan dengan tombol Back
    }
}
