package com.surendramaran.yolov8tflite // Pastikan ini paket Anda

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.sqrt

// ----- Definisi Data Class dan Enum (Tidak ada perubahan) -----
enum class HeadDirection {
    FORWARD, LEFT, RIGHT, UP, DOWN, SLIGHT_LEFT, SLIGHT_RIGHT, UNKNOWN
}

enum class GazeDirection {
    FORWARD, LEFT, RIGHT, UP, DOWN, UNKNOWN
}

enum class DetectionFocusMode {
    BOTH, IRIS_ONLY, HEAD_ONLY
}

data class FaceAnalytics(
    val faceId: Int,
    val landmarks: List<PointF>,
    val headPoseArrowStart: PointF?,
    val headPoseArrowEnd: PointF?,
    val headDirection: HeadDirection = HeadDirection.UNKNOWN,
    var isLookingAtBoard: Boolean = false,
    val gazeArrowStart: PointF?,
    val gazeArrowEnd: PointF?,
    val gazeDirection: GazeDirection = GazeDirection.UNKNOWN
)

data class ProcessedFaceData(
    val allFaceAnalytics: List<FaceAnalytics>
)
// ----- Akhir Definisi -----

class FaceMeshProcessor(
    private val context: Context,
    private val modelPath: String = Constants.FACEMESH_MODEL_PATH,
    private val listener: FaceMeshListener,
    private var currentDetectionFocusMode: DetectionFocusMode = DetectionFocusMode.BOTH
) {
    private var faceLandmarker: FaceLandmarker? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "FaceMeshProcessor"
        const val NOSE_TIP = 1
        const val LEFT_EYE_OUTER_CORNER = 33
        const val LEFT_EYE_INNER_CORNER = 133
        const val RIGHT_EYE_OUTER_CORNER = 263
        const val RIGHT_EYE_INNER_CORNER = 362
        const val CHIN_TIP = 152

        val LEFT_IRIS_INDICES = listOf(474, 475, 476, 477)
        val RIGHT_IRIS_INDICES = listOf(469, 470, 471, 472)

        const val LEFT_EYE_CENTER_FOR_GAZE = LEFT_EYE_INNER_CORNER
        const val RIGHT_EYE_CENTER_FOR_GAZE = RIGHT_EYE_INNER_CORNER
    }

    private var boardArea = RectF(0.25f, 0.1f, 0.75f, 0.35f)

    init {
        setupFaceLandmarker()
    }

    fun setDetectionFocusMode(mode: DetectionFocusMode) {
        this.currentDetectionFocusMode = mode
        Log.d(TAG, "Detection focus mode set to: $mode")
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelPath)
            val baseOptions = baseOptionsBuilder.build()

            val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(5)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(false)

            val options = optionsBuilder.build()
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "FaceLandmarker initialized successfully.")
        } catch (e: Exception) {
            isInitialized = false
            listener.onFaceMeshError("Init FaceLandmarker Failed: ${e.localizedMessage}")
            Log.e(TAG, "ERROR initializing FaceLandmarker", e)
        }
    }

    fun process(frameBitmap: Bitmap) {
        if (!isInitialized || faceLandmarker == null) return

        val mpImage = BitmapImageBuilder(frameBitmap).build()
        val startTime = SystemClock.uptimeMillis()

        try {
            val results = faceLandmarker?.detect(mpImage)
            val inferenceTime = SystemClock.uptimeMillis() - startTime
            listener.onFaceMeshResults(results?.let { processResults(it) }, inferenceTime)
        } catch (e: Exception) {
            listener.onFaceMeshError("FaceMesh Detection Error: ${e.localizedMessage}")
            Log.e(TAG, "Error during face landmark detection", e)
        }
    }

    private fun getAveragePoint(landmarks: List<PointF>, indices: List<Int>): PointF? {
        var sumX = 0f
        var sumY = 0f
        var validCount = 0
        indices.forEach { index ->
            if (index < landmarks.size) {
                sumX += landmarks[index].x
                sumY += landmarks[index].y
                validCount++
            }
        }
        return if (validCount > 0) PointF(sumX / validCount, sumY / validCount) else null
    }

    /**
     * --- PERBAIKAN UTAMA ---
     * Fungsi ini sekarang meniru logika dari file Python.
     * Ia menghitung perpotongan vektor dengan tepi area papan tulis (boardArea).
     * Jika tidak ada perpotongan, ia mengembalikan panah pendek.
     */
    private fun projectVectorToBoardEdges(startPoint: PointF, directionVector: PointF, board: RectF): PointF {
        val x0 = startPoint.x
        val y0 = startPoint.y
        val dx = directionVector.x
        val dy = directionVector.y

        if (abs(dx) < 1e-6 && abs(dy) < 1e-6) return startPoint

        val intersections = mutableListOf<Pair<Float, PointF>>()

        // Cek perpotongan dengan tepi papan tulis
        // Tepi Kiri (x = board.left)
        if (dx != 0f) {
            val t = (board.left - x0) / dx
            if (t > 0) {
                val y = y0 + t * dy
                if (y >= board.top && y <= board.bottom) {
                    intersections.add(t to PointF(board.left, y))
                }
            }
        }
        // Tepi Kanan (x = board.right)
        if (dx != 0f) {
            val t = (board.right - x0) / dx
            if (t > 0) {
                val y = y0 + t * dy
                if (y >= board.top && y <= board.bottom) {
                    intersections.add(t to PointF(board.right, y))
                }
            }
        }
        // Tepi Atas (y = board.top)
        if (dy != 0f) {
            val t = (board.top - y0) / dy
            if (t > 0) {
                val x = x0 + t * dx
                if (x >= board.left && x <= board.right) {
                    intersections.add(t to PointF(x, board.top))
                }
            }
        }
        // Tepi Bawah (y = board.bottom)
        if (dy != 0f) {
            val t = (board.bottom - y0) / dy
            if (t > 0) {
                val x = x0 + t * dx
                if (x >= board.left && x <= board.right) {
                    intersections.add(t to PointF(x, board.bottom))
                }
            }
        }

        // Jika ada perpotongan, cari yang terdekat dari titik awal
        if (intersections.isNotEmpty()) {
            return intersections.minByOrNull { it.first }!!.second
        }

        // Jika tidak ada perpotongan, kembalikan panah pendek ke arah yang dituju
        val norm = sqrt(dx * dx + dy * dy)
        val defaultLength = 0.04f // Panjang panah pendek dalam koordinat ternormalisasi
        return if (norm > 0) {
            PointF(x0 + (dx / norm) * defaultLength, y0 + (dy / norm) * defaultLength)
        } else {
            startPoint
        }
    }

    private fun isPointOnBoardEdge(point: PointF, board: RectF): Boolean {
        val tolerance = 0.01f // Toleransi kecil untuk perbandingan float
        val isOnHorizontalEdge = (abs(point.y - board.top) < tolerance || abs(point.y - board.bottom) < tolerance) && (point.x >= board.left && point.x <= board.right)
        val isOnVerticalEdge = (abs(point.x - board.left) < tolerance || abs(point.x - board.right) < tolerance) && (point.y >= board.top && point.y <= board.bottom)
        return isOnHorizontalEdge || isOnVerticalEdge
    }

    private fun estimateHeadDirection(noseTip: PointF, midEyes: PointF, leftEyeOuter: PointF, rightEyeOuter: PointF): HeadDirection {
        val faceWidthNorm = abs(rightEyeOuter.x - leftEyeOuter.x)
        if (faceWidthNorm < 0.01f) return HeadDirection.UNKNOWN

        val dx = (noseTip.x - midEyes.x) / faceWidthNorm
        val turnThreshold = 0.15f
        val slightTurnThreshold = 0.07f

        return when {
            dx < -turnThreshold -> HeadDirection.LEFT
            dx > turnThreshold -> HeadDirection.RIGHT
            dx < -slightTurnThreshold -> HeadDirection.SLIGHT_LEFT
            dx > slightTurnThreshold -> HeadDirection.SLIGHT_RIGHT
            else -> HeadDirection.FORWARD
        }
    }

    private fun estimateGazeDirection(midEyesForGaze: PointF, midPupil: PointF, faceWidthForGaze: Float): GazeDirection {
        val dx = midPupil.x - midEyesForGaze.x
        val dy = midPupil.y - midEyesForGaze.y

        val gazeHorizontalThreshold = faceWidthForGaze * 0.018f
        val gazeVerticalThreshold = faceWidthForGaze * 0.018f

        return when {
            abs(dx) < gazeHorizontalThreshold && abs(dy) < gazeVerticalThreshold -> GazeDirection.FORWARD
            dx > gazeHorizontalThreshold -> GazeDirection.RIGHT
            dx < -gazeHorizontalThreshold -> GazeDirection.LEFT
            dy > gazeVerticalThreshold -> GazeDirection.DOWN
            dy < -gazeVerticalThreshold -> GazeDirection.UP
            else -> GazeDirection.UNKNOWN
        }
    }

    private fun processResults(results: FaceLandmarkerResult): ProcessedFaceData? {
        if (results.faceLandmarks().isEmpty()) return null
        val allAnalytics = mutableListOf<FaceAnalytics>()

        results.faceLandmarks().forEachIndexed { index, faceLandmarkList ->
            val landmarks = faceLandmarkList.map { PointF(it.x(), it.y()) }

            var headPoseStart: PointF? = null
            var headPoseEnd: PointF? = null
            var headDir = HeadDirection.UNKNOWN
            var gazeStart: PointF? = null
            var gazeEnd: PointF? = null
            var gazeDir = GazeDirection.UNKNOWN
            var irisDataAvailable = false

            val nose = landmarks.getOrNull(NOSE_TIP)
            val leftEyeInner = landmarks.getOrNull(LEFT_EYE_INNER_CORNER)
            val rightEyeInner = landmarks.getOrNull(RIGHT_EYE_INNER_CORNER)
            val leftEyeOuter = landmarks.getOrNull(LEFT_EYE_OUTER_CORNER)
            val rightEyeOuter = landmarks.getOrNull(RIGHT_EYE_OUTER_CORNER)

            if (nose != null && leftEyeInner != null && rightEyeInner != null && leftEyeOuter != null && rightEyeOuter != null) {
                headPoseStart = PointF((leftEyeInner.x + rightEyeInner.x) / 2f, (leftEyeInner.y + rightEyeInner.y) / 2f)
                val headDirectionVector = PointF(nose.x - headPoseStart.x, nose.y - headPoseStart.y)
                headPoseEnd = projectVectorToBoardEdges(headPoseStart, headDirectionVector, boardArea)
                headDir = estimateHeadDirection(nose, headPoseStart, leftEyeOuter, rightEyeOuter)
            }

            val leftPupil = getAveragePoint(landmarks, LEFT_IRIS_INDICES)
            val rightPupil = getAveragePoint(landmarks, RIGHT_IRIS_INDICES)
            val leftEyeCenter = landmarks.getOrNull(LEFT_EYE_CENTER_FOR_GAZE)
            val rightEyeCenter = landmarks.getOrNull(RIGHT_EYE_CENTER_FOR_GAZE)

            if (leftPupil != null && rightPupil != null && leftEyeCenter != null && rightEyeCenter != null) {
                irisDataAvailable = true
                gazeStart = PointF((leftEyeCenter.x + rightEyeCenter.x) / 2f, (leftEyeCenter.y + rightEyeCenter.y) / 2f)
                val midPupil = PointF((leftPupil.x + rightPupil.x) / 2f, (leftPupil.y + rightPupil.y) / 2f)
                val gazeDirectionVector = PointF(midPupil.x - gazeStart.x, midPupil.y - gazeStart.y)
                gazeEnd = projectVectorToBoardEdges(gazeStart, gazeDirectionVector, boardArea)

                val faceWidth = abs(rightEyeOuter!!.x - leftEyeOuter!!.x)
                if (faceWidth > 0.01f) gazeDir = estimateGazeDirection(gazeStart, midPupil, faceWidth)
            }

            // Penentuan fokus sekarang didasarkan pada apakah proyeksi vektor menyentuh papan tulis
            val headLooksAtTarget = headPoseEnd?.let { isPointOnBoardEdge(it, boardArea) } ?: false
            val gazeLooksAtTarget = gazeEnd?.let { isPointOnBoardEdge(it, boardArea) } ?: false

            val lookingAtBoard = when (currentDetectionFocusMode) {
                DetectionFocusMode.BOTH -> headLooksAtTarget && (gazeLooksAtTarget || !irisDataAvailable)
                DetectionFocusMode.IRIS_ONLY -> if (irisDataAvailable) gazeLooksAtTarget else false
                DetectionFocusMode.HEAD_ONLY -> headLooksAtTarget
            }

            allAnalytics.add(
                FaceAnalytics(
                    faceId = index, landmarks = landmarks,
                    headPoseArrowStart = headPoseStart, headPoseArrowEnd = headPoseEnd,
                    headDirection = headDir, isLookingAtBoard = lookingAtBoard,
                    gazeArrowStart = gazeStart, gazeArrowEnd = gazeEnd,
                    gazeDirection = gazeDir
                )
            )
        }
        return if (allAnalytics.isNotEmpty()) ProcessedFaceData(allAnalytics) else null
    }

    fun updateBoardArea(newArea: RectF) {
        this.boardArea = newArea
        Log.d(TAG, "Area papan tulis diupdate di FaceMeshProcessor: $newArea")
    }

    fun clear() {
        faceLandmarker?.close()
        faceLandmarker = null
        isInitialized = false
        Log.d(TAG, "FaceMeshProcessor closed.")
    }

    interface FaceMeshListener {
        fun onFaceMeshResults(results: ProcessedFaceData?, inferenceTime: Long)
        fun onFaceMeshError(error: String)
    }
}
