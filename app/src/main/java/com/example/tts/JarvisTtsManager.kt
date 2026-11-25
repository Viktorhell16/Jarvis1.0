package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.ui.i18n.AppLanguage
import java.util.Locale

class JarvisTtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private var currentLanguage: AppLanguage = AppLanguage.RU

    override fun onInit(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            applyLocale(currentLanguage)
        }
    }

    fun setLanguage(language: AppLanguage) {
        currentLanguage = language
        if (isReady) {
            applyLocale(language)
        }
    }

    private fun applyLocale(language: AppLanguage) {
        val locale = when (language) {
            AppLanguage.RU -> Locale("ru", "RU")
            AppLanguage.UK -> Locale("uk", "UA")
            AppLanguage.EN -> Locale.US
        }
        tts?.language = locale
    }

    fun speak(rawText: String, language: AppLanguage = currentLanguage) {
        if (!isReady) return
        setLanguage(language)
        val sanitized = sanitizeForSpeech(rawText)
        if (sanitized.isBlank()) return
        tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        private val CODE_FENCE_REGEX = Regex("```[\\s\\S]*?```")
        private val INLINE_CODE_REGEX = Regex("`([^`]*)`")
        private val JSON_BLOCK_REGEX = Regex("\\{[\\s\\S]*?\\}")
        private val URL_REGEX = Regex("https?://\\S+")
        private val MARKDOWN_SYMBOLS_REGEX = Regex("(\\*\\*|__|\\*|_|#{1,6}\\s?|~~|>|\\[|\\])")
        private val EXTRA_SPACES_REGEX = Regex("\\s{2,}")

        fun sanitizeForSpeech(input: String): String {
            return input
                .replace(CODE_FENCE_REGEX, " ")
                .replace(JSON_BLOCK_REGEX, " ")
                .replace(URL_REGEX, " ")
                .replace(INLINE_CODE_REGEX, "$1")
                .replace(MARKDOWN_SYMBOLS_REGEX, "")
                .replace(EXTRA_SPACES_REGEX, " ")
                .trim()
        }
    }
}
