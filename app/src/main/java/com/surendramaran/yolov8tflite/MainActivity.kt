package com.surendramaran.yolov8tflite // Pastikan paket ini sesuai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.surendramaran.yolov8tflite.Constants.FACEMESH_MODEL_PATH
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(),
    Detector.DetectorListener,
    FaceMeshProcessor.FaceMeshListener,
    BoardSettingsDialogFragment.BoardSettingsListener {

    private data class StudentFocusResult(
        val totalStudents: Int,
        val focusedStudents: Int,
        val processedStates: Map<Int, Pair<Boolean, HeadDirection>>
    )

    private lateinit var binding: ActivityMainBinding
    private var isFrontCamera = false

    // Variabel untuk status analisis dan animasi
    private var isAnalysisRunning = false
    private var blinkingAnimation: Animation? = null
    private var analysisSessionStartTime: Long = 0L

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector
    private lateinit var faceMeshProcessor: FaceMeshProcessor

    private lateinit var cameraExecutor: ExecutorService
    private var lastYoloResults: List<BoundingBox> = emptyList()

    private var yoloInferenceTime: Long = 0
    private var faceMeshInferenceTime: Long = 0

    private lateinit var barChart: BarChart

    // Akumulator untuk chart
    private var accumulatedFocusedFrames: Long = 0
    private var accumulatedUnfocusedFrames: Long = 0

    // --- PERBAIKAN: Deklarasi variabel yang hilang ---
    private var chartUpdateCounter = 0
    private val CHART_UPDATE_INTERVAL_IN_FRAMES = 60
    // ----------------------------------------------------

    // Akumulator terpisah untuk sesi database
    private var intervalFocusedFrames: Long = 0
    private var intervalUnfocusedFrames: Long = 0

    // Pengaturan
    private var currentBoardArea = RectF(0.25f, 0.15f, 0.75f, 0.40f)
    private var currentDetectionMode = "Both"
    private var currentScaleFactor = 1.0f
    private var currentSkipFrames = 1
    private var analyzerFrameCounter = 0

    // Media
    private var mediaPlayer: MediaPlayer? = null
    private var isPhoneDetectedInPreviousFrame = false
    private var isPhoneAlertEnabled = true

    // Database
    private val db by lazy { AppDatabase.getDatabase(this) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        barChart = binding.barChartFocus
        setupBarChart()
        updateFocusChart()
        setupUIListeners()

        blinkingAnimation = AnimationUtils.loadAnimation(this, R.anim.blinking_rec)

        loadAppSettings()

        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

        faceMeshProcessor = FaceMeshProcessor(baseContext, FACEMESH_MODEL_PATH, this)
        applyCurrentSettings()

        cameraExecutor = Executors.newSingleThreadExecutor()
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.alert)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating MediaPlayer: ${e.message}")
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun setupUIListeners() {
        binding.buttonSwitchCamera.setOnClickListener {
            stopCurrentAnalysis()
            isFrontCamera = !isFrontCamera
            bindCameraUseCases()
        }

        binding.buttonSettings.setOnClickListener {
            stopCurrentAnalysis()
            BoardSettingsDialogFragment().show(supportFragmentManager, BoardSettingsDialogFragment.TAG)
        }

        binding.buttonHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.buttonToggleChart.setOnClickListener {
            binding.cardChart.visibility = if (binding.cardChart.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.fabStartStop.setOnClickListener {
            if (isAnalysisRunning) {
                stopCurrentAnalysis()
            } else {
                startNewAnalysis()
            }
        }
    }

    private fun startNewAnalysis() {
        isAnalysisRunning = true
        resetChartAccumulators()
        analysisSessionStartTime = System.currentTimeMillis()

        binding.fabStartStop.text = "Hentikan Analisis"
        binding.fabStartStop.setIconResource(R.drawable.baseline_adjust_24)

        binding.recIndicator.visibility = View.VISIBLE
        binding.recIndicator.startAnimation(blinkingAnimation)

        Toast.makeText(this, "Analisis dimulai...", Toast.LENGTH_SHORT).show()
    }

    private fun stopCurrentAnalysis() {
        if (!isAnalysisRunning) return

        isAnalysisRunning = false

        val durationInMillis = System.currentTimeMillis() - analysisSessionStartTime
        val durationInSeconds = (durationInMillis / 1000).toInt()

        saveFinalSessionToDatabase(lastYoloResults.count { isStudent(it) }, durationInSeconds)

        binding.fabStartStop.text = "Mulai Analisis"
        binding.fabStartStop.setIconResource(R.drawable.baseline_play_arrow_24)

        binding.recIndicator.clearAnimation()
        binding.recIndicator.visibility = View.GONE

        Toast.makeText(this, "Analisis dihentikan. Data tersimpan.", Toast.LENGTH_SHORT).show()
    }

    private fun playSound() {
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
            Log.d(TAG, "Phone detection sound played.")
        }
    }

    private fun resetChartAccumulators() {
        accumulatedFocusedFrames = 0
        accumulatedUnfocusedFrames = 0
        chartUpdateCounter = 0
        intervalFocusedFrames = 0
        intervalUnfocusedFrames = 0
        updateFocusChart()
    }

    private fun loadAppSettings() {
        val sharedPrefs = getSharedPreferences("AppGlobalSettings", Context.MODE_PRIVATE)
        currentBoardArea = RectF(
            sharedPrefs.getFloat("board_x1", 0.25f),
            sharedPrefs.getFloat("board_y1", 0.15f),
            sharedPrefs.getFloat("board_x2", 0.75f),
            sharedPrefs.getFloat("board_y2", 0.40f)
        )
        currentDetectionMode = sharedPrefs.getString("detection_mode", "Both") ?: "Both"
        currentScaleFactor = sharedPrefs.getFloat("scale_factor", 1.0f)
        currentSkipFrames = sharedPrefs.getInt("skip_frames", 1).coerceAtLeast(1)
        isPhoneAlertEnabled = sharedPrefs.getBoolean("phone_alert_enabled", true)
    }

    private fun applyCurrentSettings() {
        faceMeshProcessor.updateBoardArea(currentBoardArea)
        binding.overlay.updateBoardArea(currentBoardArea)

        val focusMode = when(currentDetectionMode) {
            "Iris Detection Only" -> DetectionFocusMode.IRIS_ONLY
            "Head Pose Only" -> DetectionFocusMode.HEAD_ONLY
            else -> DetectionFocusMode.BOTH
        }
        faceMeshProcessor.setDetectionFocusMode(focusMode)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val rotation = binding.viewFinder.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
            .build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analyzerFrameCounter = 0
        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            analyzerFrameCounter++
            if (analyzerFrameCounter % currentSkipFrames != 0) {
                imageProxy.close()
                return@setAnalyzer
            }
            if (analyzerFrameCounter > 10000) analyzerFrameCounter = 0

            val bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            imageProxy.use { it.planes[0].buffer.rewind(); bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer) }

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

            val finalBitmap = if (currentScaleFactor in 0.1f..0.99f) {
                val newWidth = (rotatedBitmap.width * currentScaleFactor).toInt()
                val newHeight = (rotatedBitmap.height * currentScaleFactor).toInt()
                if (newWidth > 0 && newHeight > 0) Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true) else rotatedBitmap
            } else {
                rotatedBitmap
            }

            detector.detect(finalBitmap)
            faceMeshProcessor.process(finalBitmap)
        }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        this.lastYoloResults = boundingBoxes
        this.yoloInferenceTime = inferenceTime

        val isPhoneCurrentlyDetected = boundingBoxes.any {
            it.clsName.equals("cell phone", ignoreCase = true)
        }

        if (isAnalysisRunning && isPhoneAlertEnabled && isPhoneCurrentlyDetected && !isPhoneDetectedInPreviousFrame) {
            playSound()
        }

        isPhoneDetectedInPreviousFrame = isPhoneCurrentlyDetected
    }

    override fun onEmptyDetect() {
        this.lastYoloResults = emptyList()
        this.yoloInferenceTime = 0
        isPhoneDetectedInPreviousFrame = false
        updateStatsAndOverlay(null)
    }

    override fun onFaceMeshResults(processedData: ProcessedFaceData?, inferenceTime: Long) {
        this.faceMeshInferenceTime = inferenceTime
        updateStatsAndOverlay(processedData)
    }

    override fun onFaceMeshError(error: String) {
        Log.e(TAG, "FaceMesh Error: $error")
        runOnUiThread {
            Toast.makeText(this, "FaceMesh Error: $error", Toast.LENGTH_SHORT).show()
            binding.overlay.clear()
        }
        updateStatsAndOverlay(null)
    }

    private fun updateStatsAndOverlay(currentFaceData: ProcessedFaceData?) {
        val focusResult = processFocusData(lastYoloResults, currentFaceData)

        runOnUiThread {
            binding.inferenceTime.text = "${yoloInferenceTime + faceMeshInferenceTime}ms"
            binding.textViewTotalStudents.text = "Siswa: ${focusResult.totalStudents}"
            binding.textViewFocusedStudents.text = "Fokus: ${focusResult.focusedStudents}"

            // Seluruh logika akumulasi dan penyimpanan hanya berjalan jika analisis aktif
            if (isAnalysisRunning) {
                accumulatedFocusedFrames += focusResult.focusedStudents
                accumulatedUnfocusedFrames += (focusResult.totalStudents - focusResult.focusedStudents)

                // Chart di-update secara berkala untuk feedback visual
                chartUpdateCounter++
                if (chartUpdateCounter >= CHART_UPDATE_INTERVAL_IN_FRAMES) {
                    updateFocusChart()
                    chartUpdateCounter = 0
                }
            }

            binding.overlay.apply {
                setYoloResults(lastYoloResults)
                setFaceMeshResults(currentFaceData)
                setProcessedFocusStates(focusResult.processedStates)
            }
        }
    }

    private fun saveFinalSessionToDatabase(totalStudents: Int, durationInSeconds: Int) {
        if (accumulatedFocusedFrames > 0 || accumulatedUnfocusedFrames > 0) {
            lifecycleScope.launch {
                val session = FocusSession(
                    timestamp = System.currentTimeMillis(),
                    focusedCount = accumulatedFocusedFrames.toInt(),
                    unfocusedCount = accumulatedUnfocusedFrames.toInt(),
                    totalStudents = totalStudents,
                    durationInSeconds = durationInSeconds
                )
                db.focusSessionDao().insert(session)
                Log.d(TAG, "Sesi final disimpan ke database: $session")
            }
        } else {
            Log.d(TAG, "Tidak ada aktivitas, sesi tidak disimpan.")
        }
    }

    private fun processFocusData(yoloResults: List<BoundingBox>, faceData: ProcessedFaceData?): StudentFocusResult {
        val processedStates = mutableMapOf<Int, Pair<Boolean, HeadDirection>>()
        var focusedCount = 0

        val studentBoxes = yoloResults.filter { isStudent(it) }
        val totalStudents = studentBoxes.size

        if (faceData != null && faceData.allFaceAnalytics.isNotEmpty() && studentBoxes.isNotEmpty()) {
            val availableFaceAnalytics = faceData.allFaceAnalytics.toMutableList()

            studentBoxes.forEach { yoloBox ->
                val yoloIndex = yoloResults.indexOf(yoloBox)
                val yoloRect = RectF(yoloBox.x1, yoloBox.y1, yoloBox.x2, yoloBox.y2)

                val bestMatch = availableFaceAnalytics.minByOrNull { face ->
                    if (face.landmarks.size > FaceMeshProcessor.NOSE_TIP) {
                        val noseTip = face.landmarks[FaceMeshProcessor.NOSE_TIP]
                        if (yoloRect.contains(noseTip.x, noseTip.y)) {
                            sqrt((noseTip.x - yoloBox.cx).pow(2) + (noseTip.y - yoloBox.cy).pow(2))
                        } else {
                            Float.MAX_VALUE
                        }
                    } else Float.MAX_VALUE
                }

                if (bestMatch != null) {
                    availableFaceAnalytics.remove(bestMatch)
                    val isLooking = isStudentFocused(bestMatch)
                    if (isLooking) focusedCount++
                    processedStates[yoloIndex] = Pair(isLooking, bestMatch.headDirection)
                } else {
                    processedStates[yoloIndex] = Pair(false, HeadDirection.UNKNOWN)
                }
            }
        } else {
            studentBoxes.forEach {
                val yoloIndex = yoloResults.indexOf(it)
                processedStates[yoloIndex] = Pair(false, HeadDirection.UNKNOWN)
            }
        }
        return StudentFocusResult(totalStudents, focusedCount, processedStates)
    }

    private fun isStudent(box: BoundingBox): Boolean {
        return box.clsName.equals("person", ignoreCase = true) ||
                box.clsName.equals("face", ignoreCase = true) ||
                box.clsName.equals("kepala", ignoreCase = true)
    }

    private fun isStudentFocused(analytics: FaceAnalytics): Boolean {
        val isLookingAtBoardByHeadPose = analytics.isLookingAtBoard &&
                (analytics.headDirection in listOf(HeadDirection.FORWARD, HeadDirection.SLIGHT_LEFT, HeadDirection.SLIGHT_RIGHT))

        return when (currentDetectionMode) {
            "Both" -> isLookingAtBoardByHeadPose
            "Head Pose Only" -> isLookingAtBoardByHeadPose
            "Iris Detection Only" -> false
            else -> isLookingAtBoardByHeadPose
        }
    }

    override fun onBoardSettingsSaved(
        x1: Float, y1: Float, x2: Float, y2: Float,
        detectionMode: String, scaleFactor: Float, skipFrames: Int,
        phoneAlertEnabled: Boolean
    ) {
        currentBoardArea = RectF(x1, y1, x2, y2)
        currentDetectionMode = detectionMode
        currentScaleFactor = scaleFactor.coerceIn(0.1f, 1.0f)
        currentSkipFrames = skipFrames.coerceAtLeast(1)
        this.isPhoneAlertEnabled = phoneAlertEnabled

        Log.d(TAG, "Pengaturan Diperbarui: Papan=$currentBoardArea, Mode=$currentDetectionMode, Peringatan Ponsel=$isPhoneAlertEnabled")
        applyCurrentSettings()
        resetChartAccumulators()
        Toast.makeText(this, "Pengaturan diterapkan.", Toast.LENGTH_SHORT).show()
    }

    private fun setupBarChart() {
        barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setFitBars(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = false
            axisRight.isEnabled = false

            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(listOf("Fokus", "Tdk Fokus"))
                textColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_onSurface)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                granularity = 1f
                textColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_onSurface)
            }
        }
        barChart.invalidate()
    }

    private fun updateFocusChart() {
        val totalAccumulated = accumulatedFocusedFrames + accumulatedUnfocusedFrames
        if (totalAccumulated <= 0) {
            barChart.data = null
            barChart.invalidate()
            return
        }

        val entries = arrayListOf(
            BarEntry(0f, accumulatedFocusedFrames.toFloat()),
            BarEntry(1f, accumulatedUnfocusedFrames.toFloat())
        )

        val dataSet = BarDataSet(entries, "Akumulasi Status Fokus").apply {
            colors = listOf(
                ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light),
                ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light)
            )
            valueTextColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_onSurface)
            valueTextSize = 12f
        }

        barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
        barChart.axisLeft.axisMaximum = totalAccumulated.toFloat()
        barChart.invalidate()
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera() else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
        // Hentikan analisis jika aplikasi di-pause untuk mencegah data tidak konsisten
        stopCurrentAnalysis()
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        faceMeshProcessor.clear()
        cameraExecutor.shutdown()

        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
