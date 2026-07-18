package com.adas.bountyrun.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Spoken ADAS alerts (spec §25) via Android TextToSpeech. Maps the voice keys
 * emitted by the ADAS manager to natural-language lines, and also speaks free
 * text for UI confirmations. Robust to devices without TTS data: it reports
 * availability so the UI can inform the driver instead of failing silently.
 */
class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var enabled = true

    /** True once TTS is initialised with a usable language. Read by the UI. */
    var available = false
        private set
    /** Optional callback fired once init completes (true = usable). */
    var onReady: ((Boolean) -> Unit)? = null

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
        val engine = tts
        if (status == TextToSpeech.SUCCESS && engine != null) {
            // Prefer US English, fall back to the device default if unavailable.
            var langOk = engine.setLanguage(Locale.US)
            if (langOk == TextToSpeech.LANG_MISSING_DATA || langOk == TextToSpeech.LANG_NOT_SUPPORTED) {
                langOk = engine.setLanguage(Locale.getDefault())
            }
            available = langOk != TextToSpeech.LANG_MISSING_DATA &&
                langOk != TextToSpeech.LANG_NOT_SUPPORTED
            if (available) {
                engine.setSpeechRate(1.05f)
                engine.setPitch(1.0f)
                // Route as navigation guidance so it plays over game/media audio.
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("deprecated") override fun onError(utteranceId: String?) {}
                })
                ready = true
            }
        }
        onReady?.invoke(available)
    }

    /** Speak the alert line mapped from [voiceKey]; interrupts for urgency. */
    fun speak(voiceKey: String) {
        val line = lines[voiceKey] ?: return
        say(line, voiceKey)
    }

    /** Speak arbitrary [line] text (used for UI confirmations). */
    fun speakNow(line: String) = say(line, "ui")

    private fun say(line: String, id: String) {
        if (!enabled || !ready) return
        tts?.speak(line, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        available = false
    }
}
