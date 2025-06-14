package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class Detector(
    private val context: Context,
    private val modelPath: String = Constants.MODEL_PATH,
    private val labelPath: String = Constants.LABELS_PATH,
    private val detectorListener: DetectorListener
) {
    private var interpreter: Interpreter? = null
    private var labels = mutableListOf<String>()

    private val desiredLabelsMap = mutableMapOf<String, Int>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numPredElements = 0
    private var numPredictions = 0
    private var outputIsDxN: Boolean = true

    private val yoloImageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .build()

    fun setup() {
        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options()
            options.numThreads = 4

            val compatList = CompatibilityList()
            if(compatList.isDelegateSupportedOnThisDevice){
                val delegateOptions = compatList.bestOptionsForThisDevice
                val gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.i("Detector", "GPU Delegate is supported and has been enabled.")
                detectorListener.onDelegateStatus("Akselerasi GPU berhasil diaktifkan.")
            } else {
                Log.w("Detector", "GPU Delegate is not supported on this device. Using CPU instead.")
                detectorListener.onDelegateStatus("GPU tidak didukung, menggunakan CPU.")
            }

            interpreter = Interpreter(model, options)

            val inputTensor = interpreter?.getInputTensor(0) ?: throw IllegalStateException("Input tensor not found.")
            val inputShape = inputTensor.shape()
            tensorWidth = inputShape[2]
            tensorHeight = inputShape[1]

            val outputTensor = interpreter?.getOutputTensor(0) ?: throw IllegalStateException("Output tensor not found.")
            val outputShape = outputTensor.shape()

            if (outputShape.size == 3 && outputShape[0] == 1) {
                if (outputShape[1] < outputShape[2]) {
                    numPredElements = outputShape[1]
                    numPredictions = outputShape[2]
                    outputIsDxN = true
                } else {
                    numPredElements = outputShape[2]
                    numPredictions = outputShape[1]
                    outputIsDxN = false
                }
            } else {
                throw IllegalStateException("Unexpected output model shape: ${outputShape.joinToString()}")
            }

            val allLabels = mutableListOf<String>()
            val inputStream: InputStream = context.assets.open(labelPath)
            BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                lines.forEach { allLabels.add(it) }
            }

            val desiredLabelStrings = setOf("person", "cell phone")
            for ((index, label) in allLabels.withIndex()) {
                if (label in desiredLabelStrings) {
                    desiredLabelsMap[label] = index
                    labels.add(label)
                }
            }

            Log.d("Detector", "Setup successful.")
        } catch (e: Exception) {
            Log.e("Detector", "Error setting up YOLO detector: ${e.message}", e)
            detectorListener.onDelegateStatus("Gagal menginisialisasi detector.")
        }
    }

    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    fun detect(frame: Bitmap) {
        interpreter ?: return
        if (tensorWidth == 0 || tensorHeight == 0) {
            return
        }

        val inferenceTime = SystemClock.uptimeMillis()
        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resizedBitmap)
        val processedImage = yoloImageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer
        val outputBuffer = TensorBuffer.createFixedSize(interpreter!!.getOutputTensor(0).shape(), OUTPUT_IMAGE_TYPE)

        try {
            interpreter?.run(imageBuffer, outputBuffer.buffer.rewind())
        } catch (e: Exception) {
            Log.e("Detector", "Error running YOLO inference: ${e.message}", e)
            detectorListener.onEmptyDetect()
            return
        }

        val bestBoxes = bestBox(outputBuffer.floatArray)
        val finalInferenceTime = SystemClock.uptimeMillis() - inferenceTime

        if (bestBoxes.isNullOrEmpty()) {
            detectorListener.onEmptyDetect()
        } else {
            detectorListener.onDetect(bestBoxes, finalInferenceTime)
        }
    }

    private fun bestBox(array: FloatArray): List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()

        for (i in 0 until numPredictions) {
            for (labelName in desiredLabelsMap.keys) {
                val originalClassIndex = desiredLabelsMap[labelName] ?: continue
                val classScore: Float
                val scoreIndex = 4 + originalClassIndex

                if (outputIsDxN) {
                    classScore = array[scoreIndex * numPredictions + i]
                } else {
                    classScore = array[i * numPredElements + scoreIndex]
                }

                if (classScore > CONFIDENCE_THRESHOLD) {
                    val newClsIndex = labels.indexOf(labelName)
                    val cxNorm: Float
                    val cyNorm: Float
                    val wNorm: Float
                    val hNorm: Float

                    if (outputIsDxN) {
                        cxNorm = array[0 * numPredictions + i]
                        cyNorm = array[1 * numPredictions + i]
                        wNorm = array[2 * numPredictions + i]
                        hNorm = array[3 * numPredictions + i]
                    } else {
                        cxNorm = array[i * numPredElements + 0]
                        cyNorm = array[i * numPredElements + 1]
                        wNorm = array[i * numPredElements + 2]
                        hNorm = array[i * numPredElements + 3]
                    }

                    val x1Norm = (cxNorm - wNorm / 2f).coerceIn(0f, 1f)
                    val y1Norm = (cyNorm - hNorm / 2f).coerceIn(0f, 1f)
                    val x2Norm = (cxNorm + wNorm / 2f).coerceIn(0f, 1f)
                    val y2Norm = (cyNorm + hNorm / 2f).coerceIn(0f, 1f)

                    boundingBoxes.add(
                        BoundingBox(
                            x1 = x1Norm, y1 = y1Norm, x2 = x2Norm, y2 = y2Norm,
                            cx = cxNorm, cy = cyNorm, w = wNorm, h = hNorm,
                            cnf = classScore, cls = newClsIndex, clsName = labelName
                        )
                    )
                }
            }
        }

        if (boundingBoxes.isEmpty()) return null
        return applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>): MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.removeFirstOrNull()

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                if (first.cls == nextBox.cls) {
                    val iou = calculateIoU(first, nextBox)
                    if (iou >= IOU_THRESHOLD) {
                        iterator.remove()
                    }
                }
            }
        }
        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        if (x1 >= x2 || y1 >= y2) return 0f

        val intersectionArea = (x2 - x1) * (y2 - y1)
        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    // Perbarui interface untuk menyertakan callback status
    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
        fun onDelegateStatus(statusMessage: String) // Notifikasi baru
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3F
        private const val IOU_THRESHOLD = 0.5F
    }
}