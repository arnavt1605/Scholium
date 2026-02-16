package com.example.scholium.utils

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OCRHelper {

    // We create the recognizer once to save performance
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // A suspend function that pauses the app until OCR is done
    suspend fun extractTextFromBitmap(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Success! Resume with the text
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    // Failure! Resume with error
                    continuation.resumeWithException(e)
                }
        }
    }
}