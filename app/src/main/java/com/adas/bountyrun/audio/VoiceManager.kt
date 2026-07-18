package com.adas.bountyrun.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Spoken ADAS alerts (spec §25) via Android TextToSpeech. Maps the voice keys
 * emitted by the ADAS manager to natural-language lines. Fails silently if TTS
 * is unavailable so gameplay is never blocked.
 */
class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var enabled = true

    private val lines = mapOf(
        "va_pedestrian" to "Pedestrian detected",
        "va_collision" to "Collision risk ahead",
        "va_brake" to "Apply brakes",
        "va_lane" to "Lane departure detected",
        "va_blindspot" to "Vehicle in blind spot",
        "va_attention" to "Driver attention required",
        "va_aeb" to "Automatic emergency braking activated",
        "va_police" to "Police pursuit initiated",
        "va_bounty_low" to "Bounty critically low",
        "va_bonus" to "Safe driving bonus awarded"
    )

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.05f)
            ready = true
        }
    }

    /** Speak the line for [voiceKey]; interrupts the previous line for urgency. */
    fun speak(voiceKey: String) {
        if (!enabled || !ready) return
        val line = lines[voiceKey] ?: return
        tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, voiceKey)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
