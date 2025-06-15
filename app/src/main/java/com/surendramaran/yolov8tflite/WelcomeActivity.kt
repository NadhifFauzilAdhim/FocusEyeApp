package com.surendramaran.yolov8tflite

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.databinding.ActivityWelcomeBinding

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private var isRedirecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWelcomeScreen()
    }

    private fun setupWelcomeScreen() {
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonContinue.setOnClickListener {
            showLoader()
            Handler(Looper.getMainLooper()).postDelayed({
                redirectToMain()
            }, 1500)
        }
    }

    private fun showLoader() {
        binding.buttonContinue.isEnabled = false
        binding.buttonContinue.visibility = View.INVISIBLE

        binding.welcomeIllustration.visibility = View.INVISIBLE
        binding.welcomeTitle.visibility = View.INVISIBLE
        binding.welcomeDescription.visibility = View.INVISIBLE
        binding.footerText.visibility = View.INVISIBLE

        binding.loaderGroup.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        binding.buttonContinue.isEnabled = true
        binding.buttonContinue.visibility = View.VISIBLE

        binding.welcomeIllustration.visibility = View.VISIBLE
        binding.welcomeTitle.visibility = View.VISIBLE
        binding.welcomeDescription.visibility = View.VISIBLE
        binding.footerText.visibility = View.VISIBLE

        binding.loaderGroup.visibility = View.GONE
    }

    private fun redirectToMain() {
        isRedirecting = true
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        if (binding.loaderGroup.visibility == View.VISIBLE && !isRedirecting) {
            hideLoader()
        }
    }
}
