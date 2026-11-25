package com.example.domain.router

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import com.example.accessibility.JarvisAccessibilityService
import com.example.ui.i18n.AppLanguage
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.sqrt

sealed class RouteDecision {
    data class LocalHandled(
        val replyText: String,
        val actionTag: String = "Local Router • 0 tokens"
    ) : RouteDecision()

    data class ConfirmationRequired(
        val replyText: String,
        val actionPayloadJson: String,
        val actionTag: String = "Device Agent • Confirmation"
    ) : RouteDecision()

    data object ForwardToLlm : RouteDecision()
}

class CommandRouter(private val context: Context) {

    fun route(input: String, language: AppLanguage): RouteDecision {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return RouteDecision.ForwardToLlm
        val lower = trimmed.lowercase()

        // 1. 0-Token Math Evaluator
        tryEvaluateMath(trimmed)?.let { resultStr ->
            val label = when (language) {
                AppLanguage.RU -> "Результат вычисления: **$resultStr**"
                AppLanguage.UK -> "Результат обчислення: **$resultStr**"
                AppLanguage.EN -> "Calculation result: **$resultStr**"
            }
            return RouteDecision.LocalHandled(label, "Offline Calculator • 0 tokens")
        }

        // 2. 0-Token Timer & Alarm Commands
        parseTimerCommand(lower)?.let { seconds ->
            return launchTimerIntent(seconds, language)
        }
        parseAlarmCommand(lower)?.let { (hour, minute) ->
            return launchAlarmIntent(hour, minute, language)
        }

        // 3. 0-Token Media Controls & YouTube Music Search
        parseMediaCommand(trimmed, lower, language)?.let { return it }

        // 4. 0-Token App Launch Requests (PackageManager fuzzy match)
        parseAppLaunchCommand(trimmed, lower, language)?.let { return it }

        // 5. On-demand Accessibility Screen Agent commands
        parseAccessibilityAgentCommand(trimmed, lower, language)?.let { return it }

        return RouteDecision.ForwardToLlm
    }

    fun executeConfirmedAction(payloadJson: String, language: AppLanguage): String {
        return try {
            val obj = JSONObject(payloadJson)
            val type = obj.optString("type")
            val service = JarvisAccessibilityService.instance
            if (service == null && type.startsWith("A11Y_")) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return when (language) {
                    AppLanguage.RU -> "Служба Accessibility не включена. Открываю настройки специальных возможностей для активации JARVIS."
                    AppLanguage.UK -> "Службу Accessibility не увімкнено. Відкриваю налаштування спеціальних можливостей для активації JARVIS."
                    AppLanguage.EN -> "Accessibility service is not enabled. Opening Accessibility Settings so you can enable JARVIS."
                }
            }

            when (type) {
                "A11Y_CLICK" -> {
                    val target = obj.optString("target")
                    val ok = service?.clickNodeByText(target) == true
                    if (ok) {
                        when (language) {
                            AppLanguage.RU -> "Нажатие на «$target» выполнено."
                            AppLanguage.UK -> "Натискання на «$target» виконано."
                            AppLanguage.EN -> "Clicked \"$target\"."
                        }
                    } else {
                        when (language) {
                            AppLanguage.RU -> "Элемент «$target» не найден на текущем экране."
                            AppLanguage.UK -> "Елемент «$target» не знайдено на поточному екрані."
                            AppLanguage.EN -> "Element \"$target\" was not found on the active screen."
                        }
                    }
                }
                "A11Y_INPUT" -> {
                    val text = obj.optString("text")
                    val ok = service?.inputTextIntoFocusedOrFirstEditable(text) == true
                    if (ok) "Text entered: \"$text\"." else "No editable field focused on screen."
                }
                "A11Y_READ" -> {
                    val nodes = service?.captureOnDemandSnapshot().orEmpty()
                    if (nodes.isEmpty()) {
                        "No readable screen elements captured."
                    } else {
                        nodes.take(20).joinToString("\n") { "• [${it.index}] ${it.label}${if (it.isClickable) " (clickable)" else ""}" }
                    }
                }
                "A11Y_NAV" -> {
                    val nav = obj.optString("nav")
                    val ok = service?.performGlobalNavigation(nav) == true
                    if (ok) "Navigation action $nav executed." else "Navigation action failed."
                }
                else -> "Unknown action type."
            }
        } catch (e: Exception) {
            "Action execution error: ${e.message}"
        }
    }

    private fun parseAccessibilityAgentCommand(trimmed: String, lower: String, language: AppLanguage): RouteDecision? {
        val clickPrefixes = listOf("нажми на ", "кликни на ", "натисни на ", "click ", "tap ")
        clickPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val target = trimmed.substring(prefix.length).trim()
            if (target.isNotEmpty()) {
                val payload = JSONObject().put("type", "A11Y_CLICK").put("target", target).toString()
                val msg = when (language) {
                    AppLanguage.RU -> "Экранный агент готов нажать на элемент **«$target»**."
                    AppLanguage.UK -> "Екранний агент готовий натиснути на елемент **«$target»**."
                    AppLanguage.EN -> "Screen agent ready to tap **\"$target\"**."
                }
                return RouteDecision.ConfirmationRequired(msg, payload)
            }
        }

        val inputPrefixes = listOf("введи текст ", "напиши в поле ", "введи ", "type text ")
        inputPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val text = trimmed.substring(prefix.length).trim()
            if (text.isNotEmpty()) {
                val payload = JSONObject().put("type", "A11Y_INPUT").put("text", text).toString()
                val msg = when (language) {
                    AppLanguage.RU -> "Экранный агент готов ввести текст: **$text**"
                    AppLanguage.UK -> "Екранний агент готовий ввести текст: **$text**"
                    AppLanguage.EN -> "Screen agent ready to enter text: **$text**"
                }
                return RouteDecision.ConfirmationRequired(msg, payload)
            }
        }

        if (lower.contains("прочитай экран") || lower.contains("что на экране") || lower.contains("прочитай екран") || lower.contains("read screen")) {
            val payload = JSONObject().put("type", "A11Y_READ").toString()
            val msg = when (language) {
                AppLanguage.RU -> "Считать активные элементы экрана по запросу?"
                AppLanguage.UK -> "Зчитати активні елементи екрана за запитом?"
                AppLanguage.EN -> "Capture active screen elements on demand?"
            }
            return RouteDecision.ConfirmationRequired(msg, payload)
        }
        return null
    }

    private fun launchTimerIntent(seconds: Int, language: AppLanguage): RouteDecision {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val mins = seconds / 60
            val rem = seconds % 60
            val formatted = if (mins > 0) "${mins}m ${rem}s" else "${seconds}s"
            val msg = when (language) {
                AppLanguage.RU -> "Таймер установлен на **$formatted**."
                AppLanguage.UK -> "Таймер встановлено на **$formatted**."
                AppLanguage.EN -> "Timer set for **$formatted**."
            }
            RouteDecision.LocalHandled(msg, "AlarmClock Intent • 0 tokens")
        } catch (e: Exception) {
            RouteDecision.LocalHandled("Unable to launch system timer: ${e.message}", "Local Error")
        }
    }

    private fun launchAlarmIntent(hour: Int, minute: Int, language: AppLanguage): RouteDecision {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val timeStr = String.format("%02d:%02d", hour, minute)
            val msg = when (language) {
                AppLanguage.RU -> "Будильник установлен на **$timeStr**."
                AppLanguage.UK -> "Будильник встановлено на **$timeStr**."
                AppLanguage.EN -> "Alarm set for **$timeStr**."
            }
            RouteDecision.LocalHandled(msg, "AlarmClock Intent • 0 tokens")
        } catch (e: Exception) {
            RouteDecision.LocalHandled("Unable to launch system alarm: ${e.message}", "Local Error")
        }
    }

    private fun parseTimerCommand(lower: String): Int? {
        if (!lower.contains("таймер") && !lower.contains("timer")) return null
        val match = Regex("(\\d+)\\s*(сек|sec|с|мин|хв|min|м|час|год|hour|h)?").find(lower) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        val unit = match.groupValues[2]
        return when {
            unit.startsWith("час") || unit.startsWith("год") || unit.startsWith("h") -> value * 3600
            unit.startsWith("мин") || unit.startsWith("хв") || unit == "м" -> value * 60
            unit.startsWith("сек") || unit.startsWith("sec") || unit == "с" -> value
            else -> value * 60
        }.coerceAtLeast(1)
    }

    private fun parseAlarmCommand(lower: String): Pair<Int, Int>? {
        if (!lower.contains("будильник") && !lower.contains("alarm")) return null
        val match = Regex("(\\d{1,2})[:.](\\d{2})").find(lower) ?: return null
        val h = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return null
        val m = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: return null
        return h to m
    }

    private fun parseMediaCommand(trimmed: String, lower: String, language: AppLanguage): RouteDecision? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        fun dispatchKey(keyCode: Int) {
            val now = SystemClock.uptimeMillis()
            audioManager?.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }

        when {
            lower in listOf("пауза", "стоп музыка", "останови музыку", "продолжить", "играй", "play", "pause", "пауза музику") -> {
                dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val msg = when (language) {
                    AppLanguage.RU -> "Команда воспроизведения/паузы отправлена."
                    AppLanguage.UK -> "Команду відтворення/паузи надіслано."
                    AppLanguage.EN -> "Media Play/Pause toggled."
                }
                return RouteDecision.LocalHandled(msg, "MediaSession • 0 tokens")
            }
            lower in listOf("следующий трек", "дальше", "скип", "next", "next track", "наступний трек") -> {
                dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                val msg = when (language) {
                    AppLanguage.RU -> "Переключаю на следующий трек."
                    AppLanguage.UK -> "Перемикаю на наступний трек."
                    AppLanguage.EN -> "Skipped to next track."
                }
                return RouteDecision.LocalHandled(msg, "MediaSession • 0 tokens")
            }
            lower in listOf("предыдущий трек", "назад трек", "previous", "prev", "попередній трек") -> {
                dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                val msg = when (language) {
                    AppLanguage.RU -> "Возвращаюсь к предыдущему треку."
                    AppLanguage.UK -> "Повертаюся до попереднього треку."
                    AppLanguage.EN -> "Returned to previous track."
                }
                return RouteDecision.LocalHandled(msg, "MediaSession • 0 tokens")
            }
        }

        val musicPrefixes = listOf("включи песню ", "включи музыку ", "найди трек ", "поставь трек ", "увімкни пісню ", "play song ", "play music ")
        musicPrefixes.firstOrNull { lower.startsWith(it) }?.let { prefix ->
            val query = trimmed.substring(prefix.length).trim()
            if (query.isNotEmpty()) {
                return try {
                    val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    val msg = when (language) {
                        AppLanguage.RU -> "Запускаю поиск и воспроизведение: **$query**"
                        AppLanguage.UK -> "Запускаю пошук та відтворення: **$query**"
                        AppLanguage.EN -> "Searching and playing: **$query**"
                    }
                    RouteDecision.LocalHandled(msg, "Media Play Intent • 0 tokens")
                } catch (_: Exception) {
                    RouteDecision.ForwardToLlm
                }
            }
        }
        return null
    }

    private fun parseAppLaunchCommand(trimmed: String, lower: String, language: AppLanguage): RouteDecision? {
        val prefixes = listOf("открой ", "запусти ", "відкрий ", "open ", "launch ")
        val matchedPrefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return null
        val appQuery = trimmed.substring(matchedPrefix.length).trim().lowercase()
        if (appQuery.isBlank() || appQuery.split(" ").size > 4) return null

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        val bestMatch = apps.mapNotNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm)?.toString()?.trim().orEmpty()
            val pkg = resolveInfo.activityInfo?.packageName.orEmpty()
            val score = fuzzyScore(appQuery, label.lowercase(), pkg.lowercase())
            if (score > 0) Triple(score, label, pkg) else null
        }.maxByOrNull { it.first }

        return if (bestMatch != null) {
            val launchIntent = pm.getLaunchIntentForPackage(bestMatch.third)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val msg = when (language) {
                    AppLanguage.RU -> "Запускаю **${bestMatch.second}**."
                    AppLanguage.UK -> "Запускаю **${bestMatch.second}**."
                    AppLanguage.EN -> "Launching **${bestMatch.second}**."
                }
                RouteDecision.LocalHandled(msg, "App Launcher • 0 tokens")
            } else null
        } else {
            val msg = when (language) {
                AppLanguage.RU -> "Приложение «$appQuery» не найдено на устройстве."
                AppLanguage.UK -> "Додаток «$appQuery» не знайдено на пристрої."
                AppLanguage.EN -> "App \"$appQuery\" not found on this device."
            }
            RouteDecision.LocalHandled(msg, "App Launcher • 0 tokens")
        }
    }

    private fun fuzzyScore(query: String, label: String, pkg: String): Int {
        if (label == query) return 100
        if (label.startsWith(query)) return 80
        if (label.contains(query)) return 60
        if (pkg.contains(query)) return 40
        return 0
    }

    /**
     * Pure Kotlin recursive-descent math expression evaluator (0 tokens).
     */
    fun tryEvaluateMath(raw: String): String? {
        val cleaned = raw.trim()
            .removePrefix("=")
            .replace(",", ".")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .trim()
        // Require at least one operator or math function and only math characters
        if (!cleaned.any { it in "+-*/^%" || cleaned.contains("sqrt") }) return null
        if (!cleaned.matches(Regex("^[0-9.+\\-*/^%()\\ssqrt]+$"))) return null

        return try {
            val value = MathParser(cleaned).parse()
            if (value.isNaN() || value.isInfinite()) null
            else if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e12) value.toLong().toString()
            else String.format("%.6f", value).trimEnd('0').trimEnd('.')
        } catch (_: Exception) {
            null
        }
    }

    private class MathParser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < str.length) throw IllegalArgumentException("Unexpected char")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> x /= parseFactor()
                    eat('%'.code) -> x %= parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            when {
                eat('('.code) -> {
                    x = parseExpression()
                    if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
                }
                (ch in '0'.code..'9'.code) || ch == '.'.code -> {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                }
                ch in 'a'.code..'z'.code -> {
                    while (ch in 'a'.code..'z'.code) nextChar()
                    val func = str.substring(startPos, pos)
                    x = parseFactor()
                    x = when (func) {
                        "sqrt" -> sqrt(x)
                        else -> throw IllegalArgumentException("Unknown function: $func")
                    }
                }
                else -> throw IllegalArgumentException("Unexpected: ${ch.toChar()}")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
