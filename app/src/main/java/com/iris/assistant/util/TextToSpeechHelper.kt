package com.iris.assistant.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

class TextToSpeechHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pending = mutableListOf<String>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("fa", "IR")
                isReady = true
                pending.forEach { speakInternal(it) }
                pending.clear()
            }
        }
    }

    fun speak(text: String) {
        if (isReady) speakInternal(text) else pending.add(text)
    }

    private fun speakInternal(text: String) {
        val params = android.os.Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
