package com.iris.assistant.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Runs SpeechRecognizer for exactly one request/response cycle — the mic
 * indicator turns on only while actively listening for this one utterance,
 * then off. This is the reliable way to do voice input; the always-on
 * background wake-word service is a separate, optional feature.
 */
object SingleShotSpeechRecognizer {
    fun listenOnce(context: Context, onResult: (String) -> Unit, onError: (() -> Unit)? = null) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError?.invoke()
            return
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                recognizer.destroy()
                onError?.invoke()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                recognizer.destroy()
                if (!text.isNullOrBlank()) onResult(text) else onError?.invoke()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString().replace('_', '-'))
        }
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            recognizer.destroy()
            onError?.invoke()
        }
    }
}
