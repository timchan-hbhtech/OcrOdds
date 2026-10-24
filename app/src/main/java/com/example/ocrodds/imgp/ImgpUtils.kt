package com.example.ocrodds.imgp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.collections.listOf
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.graphics.set
import java.nio.IntBuffer


fun Bitmap.preprocessImage(): Bitmap {
    // Convert to grayscale
    val grayBitmap = createBitmap(this.width, this.height)
    val canvas = Canvas(grayBitmap)
    val paint = Paint()
    val colorMatrix = ColorMatrix()
    colorMatrix.setSaturation(0f) // Convert to grayscale
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(this, 0f, 0f, paint)

    // Apply contrast enhancement
    val contrastMatrix = ColorMatrix()
    contrastMatrix.set(floatArrayOf(
        1.5f, 0f, 0f, 0f, -50f, // Increase contrast
        0f, 1.5f, 0f, 0f, -50f,
        0f, 0f, 1.5f, 0f, -50f,
        0f, 0f, 0f, 1f, 0f
    ))
    paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
    canvas.drawBitmap(grayBitmap, 0f, 0f, paint)

    return grayBitmap
}

fun Bitmap.sharpen(): Bitmap {
    require(!this.isRecycled) { "Input bitmap is recycled" }
    require(this.width > 0 && this.height > 0) { "Invalid bitmap dimensions" }

    // Convert Bitmap to OpenCV Mat
    val mat = Mat()
    Utils.bitmapToMat(this, mat)

    // Convert to appropriate type for processing
    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)

    // Apply sharpening filter (Laplacian or custom kernel)
    val sharpenedMat = Mat()
    val kernel = Mat(3, 3, CvType.CV_32F).apply {
        put(0, 0, 0.0, -1.0, 0.0)
        put(1, 0, -1.0, 5.0, -1.0)
        put(2, 0, 0.0, -1.0, 0.0)
    }
    Imgproc.filter2D(mat, sharpenedMat, -1, kernel)

    // Convert back to Bitmap
    val outputBitmap = this.config?.let { config ->
        createBitmap(width, height, config)
    } ?: createBitmap(width, height)
    Utils.matToBitmap(sharpenedMat, outputBitmap)

    // Clean up
    mat.release()
    sharpenedMat.release()
    kernel.release()

    return outputBitmap
}

fun Bitmap.maskBitmapExceptRect(rect: Rect): Bitmap {
    val mat = Mat()
    val tempBitmap = this.copy(Bitmap.Config.ARGB_8888, true)
    Utils.bitmapToMat(tempBitmap, mat)

    // Ensure the Rect is within bounds
    val boundedRect = Rect(
        rect.x.coerceIn(0, mat.cols() - 1),
        rect.y.coerceIn(0, mat.rows() - 1),
        rect.width.coerceIn(0, mat.cols() - rect.x),
        rect.height.coerceIn(0, mat.rows() - rect.y)
    )

    // Create a black Mat with the same size and type
    val result = Mat.zeros(mat.size(), mat.type())

    // Copy the rectangular region from the input Mat to the result Mat
    val roi = Mat(mat, boundedRect)
    val resultRoi = Mat(result, boundedRect)
    roi.copyTo(resultRoi)

    // Convert back to Bitmap
    val outputBitmap = createBitmap(result.cols(), result.rows())
    Utils.matToBitmap(result, outputBitmap)

    // Release Mats to free memory
    mat.release()
    result.release()
    roi.release()
    resultRoi.release()

    return outputBitmap
}

fun Bitmap.rotateBitmap(degrees: Float): Bitmap {
    val matrix = Matrix().apply {
        postRotate(degrees) // Negative for counterclockwise
    }
    return Bitmap.createBitmap(
        this,
        0, 0,
        this.width,
        this.height,
        matrix,
        true
    )
}


fun Bitmap.eraseCircle(
    moveRectWidth: Int,
    moveRectHeight: Int,
    moveRightOffset: Int,
    useAvgEnvColor: Boolean = false
): Bitmap {
    val inputMat = Mat()
    Utils.bitmapToMat(this, inputMat)

    try {
        // Ensure input is in BGR format
        val mat = if (inputMat.channels() == 4) {
            val temp = Mat()
            Imgproc.cvtColor(inputMat, temp, Imgproc.COLOR_BGRA2BGR)
            temp
        } else {
            inputMat.clone() // Copy the input Mat
        }

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(grayMat, grayMat, Size(9.0, 9.0), 2.0, 2.0)

        val circles = Mat()
        for(step in 0..30 step 5) {
            Imgproc.HoughCircles(
                grayMat, circles, Imgproc.HOUGH_GRADIENT,
                1.0,
                55.0,
                105.0,
                40.0,
                15 + step,
                100
            )

            if(circles.cols() <= 20 && circles.cols() % 2 == 0) {
                break
            }
        }

        Log.d("circle", circles.cols().toString())
        if (circles.cols() > 0) {
            for (i in 0 until circles.cols()) {
                val circle = circles[0, i]
                val center = Point(circle[0], circle[1])
                val radius = circle[2].toInt()

                val sampleRadius = (radius * 1.5).toInt()
                val samplePoint = Point(
                    center.x + (sampleRadius * cos(Math.PI)), // Slightly outside, adjusted angle
                    center.y + (sampleRadius * sin(Math.PI))
                )
                val backgroundColor = if (samplePoint.x >= 0 && samplePoint.x < mat.cols() &&
                    samplePoint.y >= 0 && samplePoint.y < mat.rows() && useAvgEnvColor) {
                    var (r, g, b) = listOf(0.0, 0.0, 0.0)
                    for (offsetI in arrayOf(-5, -2, 0, 2, 5)) {
                        for (offsetJ in arrayOf(-1, 0, 1)) {
                            val sampleRGB = mat.get(samplePoint.y.toInt() + offsetI, samplePoint.x.toInt() + offsetJ)
                            r += sampleRGB[0]
                            g += sampleRGB[1]
                            b += sampleRGB[2]
                        }
                    }

                    Scalar(r / 15, g / 15, b / 15)
                } else {
                    Scalar(255.0, 255.0, 255.0) // Fallback to white if out of bounds
                }

                mat.moveRectPortionInPlace(
                    Rect(
                        (center.x + radius + 7).toInt(),
                        (center.y - radius - 40 * radius / 80).toInt(),
                        (moveRectWidth * radius / 40),
                        (radius + moveRectHeight * radius / 40)
                    ),
                    moveRightOffset,
                    backgroundColor
                )

                Imgproc.circle(mat, center, radius, backgroundColor, 18)
            }
        }

        val rgbaMat = Mat()
        Imgproc.cvtColor(mat, rgbaMat, Imgproc.COLOR_BGR2RGBA)
        val processedBitmap = createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgbaMat, processedBitmap)
        return processedBitmap
    } catch (e: Exception) {
        return this
    }
}

fun Mat.moveRectPortionInPlace(rect: Rect, offset:Int, bgColorScalar: Scalar) {
    // Check if the shifted Rect is within bounds
    val shiftedRect = Rect(rect.x + offset, rect.y, rect.width, rect.height)
    if (shiftedRect.x + shiftedRect.width > this.cols() ||
        shiftedRect.y + shiftedRect.height > this.rows()) {
        throw IllegalArgumentException("Shifted Rect exceeds Mat bounds")
    }

    // Extract the ROI (region of interest) from the original Mat
    val roi = Mat(this, rect)

    // Create a temporary Mat to hold the ROI data
    val tempRoi = Mat()
    roi.copyTo(tempRoi)

    // Optional: Clear the original Rect area (e.g., set to black)
    this.submat(rect).setTo(bgColorScalar) // Black for BGR, adjust for other formats

    // Copy the ROI to the shifted position
    val destination = Mat(this, shiftedRect)
    tempRoi.copyTo(destination)

    // Release temporary Mat to free memory
    tempRoi.release()
}

fun Bitmap.correctDistortion(): Bitmap {
    val mat = Mat()
    Utils.bitmapToMat(this, mat)

    val cameraMatrix = Mat.eye(3, 3, CvType.CV_64F)
    val distCoeffs = Mat.zeros(5, 1, CvType.CV_64F)

    val undistortedMat = Mat()
    Calib3d.undistort(mat, undistortedMat, cameraMatrix, distCoeffs)

    val outputBitmap = createBitmap(undistortedMat.cols(), undistortedMat.rows())
    Utils.matToBitmap(undistortedMat, outputBitmap)

    return outputBitmap
}

/*
- Low threshold (e.g. 30–50):
Only very dark pixels (almost black) will turn black. Everything else becomes white.
→ Result: mostly white image with only the darkest areas preserved.
- Medium threshold (e.g. 100–120):
Darker grays also turn black, so more detail is preserved.
→ Result: more balanced black/white.
- High threshold (e.g. 180+):
Even mid-tones turn black.
→ Result: much more black, less white.
 */
fun Bitmap.toBlackAndWhite(threshold: Int = 50): Bitmap {
    val width = this.width
    val height = this.height
    val buffer = IntArray(width * height)
    copyPixelsToBuffer(IntBuffer.wrap(buffer))

    for (i in buffer.indices) {
        val r = Color.red(buffer[i])
        val g = Color.green(buffer[i])
        val b = Color.blue(buffer[i])
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        buffer[i] = if (luminance < threshold) Color.BLACK else Color.WHITE
    }

    val bwBitmap = createBitmap(width, height)
    bwBitmap.setPixels(buffer, 0, width, 0, 0, width, height)
    return bwBitmap
}




fun File.correctImageOrientation(): Bitmap {
    // Decode the bitmap
    val bitmap = BitmapFactory.decodeFile(this.absolutePath)

    // Read EXIF data
    val exif = ExifInterface(this.absolutePath)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    // Rotate the bitmap if needed
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
