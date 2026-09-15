package com.iris.assistant.tools

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.iris.assistant.agent.Tool
import com.iris.assistant.agent.ToolResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * PLACEHOLDER FIXED: ML Kit's text-recognition dependency was declared
 * in build.gradle.kts but never actually called anywhere in the app.
 * This tool wires it to the photo CameraScreen just captured.
 */
class OcrTool : Tool {
    override val name = "ocr"
    override val description = "Read text from the most recently captured photo"
    override val parameters = emptyList<com.iris.assistant.agent.ToolParameter>()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val file = LastCapture.file ?: return ToolResult.Failure("هنوز عکسی نگرفتی — اول از صفحه‌ی دوربین یه عکس بگیر.")
        return try {
            val image = InputImage.fromFilePath(com.iris.assistant.IrisApp.instance, android.net.Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = suspendCancellableCoroutine<String> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resume("") }
            }
            if (text.isBlank()) ToolResult.Success("متنی توی عکس پیدا نشد.")
            else ToolResult.Success(text)
        } catch (e: Exception) {
            ToolResult.Failure("OCR ناموفق بود: ${e.message}")
        }
    }
}

/**
 * PLACEHOLDER FIXED: same situation as OCR — object-detection dependency
 * declared but unused. Wired to the same last-captured photo.
 */
class ObjectDetectionTool : Tool {
    override val name = "object_detection"
    override val description = "Identify objects in the most recently captured photo"
    override val parameters = emptyList<com.iris.assistant.agent.ToolParameter>()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val file = LastCapture.file ?: return ToolResult.Failure("هنوز عکسی نگرفتی — اول از صفحه‌ی دوربین یه عکس بگیر.")
        return try {
            val image = InputImage.fromFilePath(com.iris.assistant.IrisApp.instance, android.net.Uri.fromFile(file))
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableClassification()
                .build()
            val detector = ObjectDetection.getClient(options)
            val objects = suspendCancellableCoroutine<List<String>> { cont ->
                detector.process(image)
                    .addOnSuccessListener { list ->
                        cont.resume(list.flatMap { obj -> obj.labels.map { it.text } })
                    }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
            if (objects.isEmpty()) ToolResult.Success("چیز مشخصی توی عکس تشخیص داده نشد.")
            else ToolResult.Success("این‌ها رو تشخیص دادم: ${objects.distinct().joinToString(", ")}")
        } catch (e: Exception) {
            ToolResult.Failure("تشخیص شیء ناموفق بود: ${e.message}")
        }
    }
}
