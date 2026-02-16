package com.example.scholium.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

object PdfUtils {

    // Copy the file from the gallery (Uri) to our app's private storage
    fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "temp_paper.pdf") // Create a temp file
            val outputStream = FileOutputStream(tempFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Convert a specific page of the PDF into an Image (Bitmap)
    fun pdfToBitmap(pdfFile: File, pageIndex: Int = 0): Bitmap? {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)

            // Open the specific page
            val page = renderer.openPage(pageIndex)

            // Create a bitmap (image) with the same dimensions as the page
            // We multiply by 2 for higher quality (crisper text)
            val bitmap = createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)

            // Render the page content onto the bitmap
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Cleanup
            page.close()
            renderer.close()

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPageCount(pdfFile: File): Int {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            val count = renderer.pageCount
            renderer.close()
            fileDescriptor.close()
            count
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    // Crop the bitmap based on screen coordinates
    fun cropBitmap(original: Bitmap, cropRect: Rect, viewWidth: Float, viewHeight: Float): Bitmap? {
        return try {
            // Calculate scaling factors (Screen vs Actual Image)
            val widthScale = original.width.toFloat() / viewWidth
            val heightScale = original.height.toFloat() / viewHeight

            // Convert Screen Coordinates -> Bitmap Coordinates
            val x = (cropRect.left * widthScale).toInt().coerceAtLeast(0)
            val y = (cropRect.top * heightScale).toInt().coerceAtLeast(0)
            val width = (cropRect.width * widthScale).toInt().coerceAtMost(original.width - x)
            val height = (cropRect.height * heightScale).toInt().coerceAtMost(original.height - y)

            // Ensure we don't try to crop an empty area
            if (width <= 0 || height <= 0) return null

            Bitmap.createBitmap(original, x, y, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

