package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.MainActivity
import com.example.R
import com.example.accessibility.JarvisAccessibilityService
import com.example.data.local.EncryptedPrefsManager
import com.example.data.local.JarvisDatabase
import com.example.data.local.MessageEntity
import com.example.data.network.LlmFailoverClient
import com.example.data.network.StreamEvent
import com.example.domain.router.CommandRouter
import com.example.domain.router.RouteDecision
import com.example.telemetry.JarvisCrashReporter
import com.example.telemetry.JarvisPerformanceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingJarvisService : Service(), LifecycleOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val serviceViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = serviceViewModelStore

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var dismissView: TextView? = null
    private var panelView: View? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null

    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var db: JarvisDatabase
    private lateinit var llmClient: LlmFailoverClient
    private lateinit var router: CommandRouter

    private var inDismissZone = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val startupTrace = JarvisPerformanceTracker.startOverlayBubbleStartupTrace()
        try {
            prefs = EncryptedPrefsManager.getInstance(this)
            db = JarvisDatabase.getInstance(this)
            llmClient = LlmFailoverClient(prefs, db.providerSlotDao(), db.tokenUsageDao())
            router = CommandRouter(this)

            startForegroundNotification()
            if (Settings.canDrawOverlays(this)) {
                showBubble()
                JarvisCrashReporter.setOverlayActive(true)
                JarvisCrashReporter.logBreadcrumb("FloatingJarvisService bubble shown")
            } else {
                JarvisCrashReporter.setOverlayActive(false)
                stopSelf()
            }
        } catch (e: Exception) {
            JarvisCrashReporter.recordNonFatal(e, "overlay_service_on_create")
            stopSelf()
        } finally {
            startupTrace.stop()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "jarvis_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        startForeground(4041, notification)
    }

    private fun overlayLayoutFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun showBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val sizePx = (56 * density).toInt()
        val preset = prefs.state.value.palettePreset

        val bubble = TextView(this).apply {
            text = "J"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(preset.overlayBubbleColorInt)
                setStroke((2 * density).toInt(), preset.overlayStrokeColorInt)
            }
            elevation = 14 * density
        }

        val dismissZone = TextView(this).apply {
            text = "✕ Закрыть"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((22 * density).toInt(), (12 * density).toInt(), (22 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 26 * density
                setColor(Color.parseColor("#E61C1E26"))
                setStroke((1.5f * density).toInt(), Color.parseColor("#EF4444"))
            }
            visibility = View.GONE
            elevation = 10 * density
        }

        val dismissParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (36 * density).toInt()
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayLayoutFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).toInt()
            y = (220 * density).toInt()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    inDismissZone = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                        if (dismissZone.visibility != View.VISIBLE) {
                            dismissZone.visibility = View.VISIBLE
                        }
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy

                    // Check magnetic bottom-center dismiss zone overlap
                    val centerX = params.x + sizePx / 2f
                    val centerY = params.y + sizePx / 2f
                    val isNearDismiss = centerY > screenH - (140 * density) &&
                        abs(centerX - screenW / 2f) < (96 * density)

                    if (isNearDismiss && !inDismissZone) {
                        inDismissZone = true
                        bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        dismissZone.scaleX = 1.12f
                        dismissZone.scaleY = 1.12f
                        (dismissZone.background as? GradientDrawable)?.setColor(Color.parseColor("#E6DC2626"))
                    } else if (!isNearDismiss && inDismissZone) {
                        inDismissZone = false
                        dismissZone.scaleX = 1.0f
                        dismissZone.scaleY = 1.0f
                        (dismissZone.background as? GradientDrawable)?.setColor(Color.parseColor("#E61C1E26"))
                    }

                    // Slight magnetic pull toward center-bottom when hovering inside dismiss zone
                    if (inDismissZone) {
                        params.x = (screenW / 2f - sizePx / 2f).toInt()
                        params.y = (screenH - (96 * density) - sizePx / 2f).toInt()
                    }

                    windowManager?.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dismissZone.visibility = View.GONE
                    dismissZone.scaleX = 1.0f
                    dismissZone.scaleY = 1.0f
                    (dismissZone.background as? GradientDrawable)?.setColor(Color.parseColor("#E61C1E26"))

                    if (moved && inDismissZone) {
                        prefs.setFloatingOverlayEnabled(false)
                        cleanupOverlayViews()
                        stopSelf()
                    } else if (!moved) {
                        toggleMiniPanel()
                    }
                    inDismissZone = false
                    true
                }
                else -> false
            }
        }

        dismissView = dismissZone
        bubbleView = bubble
        try {
            windowManager?.addView(dismissZone, dismissParams)
            windowManager?.addView(bubble, params)
        } catch (e: Exception) {
            JarvisCrashReporter.recordNonFatal(e, "overlay_add_bubble_view")
            stopSelf()
        }
    }

    private fun toggleMiniPanel() {
        if (panelView != null) {
            runCatching { windowManager?.removeViewImmediate(panelView) }
            panelView = null
            return
        }

        val density = resources.displayMetrics.density
        val pad = (14 * density).toInt()
        val preset = prefs.state.value.palettePreset
        val accentArgb = preset.primary.toArgb()
        val onAccentArgb = preset.onPrimary.toArgb()
        val containerArgb = preset.primaryContainer.toArgb()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = 24 * density
                setColor(Color.parseColor("#F212141B"))
                setStroke((1.5f * density).toInt(), preset.overlayStrokeColorInt)
            }
            elevation = 16 * density
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "JARVIS MINI"
            setTextColor(accentArgb)
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { toggleMiniPanel() }
        }
        headerRow.addView(title)
        headerRow.addView(closeBtn)

        val outputText = TextView(this).apply {
            text = "Готов к быстрой команде (офлайн, YouTube, игры или перевод экрана)."
            setTextColor(Color.parseColor("#E5E7EB"))
            textSize = 13f
            setPadding(0, (8 * density).toInt(), 0, (10 * density).toInt())
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (148 * density).toInt()
            )
            addView(outputText)
        }

        // 6 YouTube & Gaming Quick Action Chips
        val quickChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (6 * density).toInt(), 0, (10 * density).toInt())
        }
        val quickActions = listOf(
            "Пауза / Плей" to "QUICK_MEDIA_PLAY_PAUSE",
            "Следующий трек" to "QUICK_MEDIA_NEXT",
            "Пересказ видео" to "QUICK_VIDEO_SUMMARY",
            "Гайд по игре" to "QUICK_GAME_GUIDE",
            "Перевод экрана" to "QUICK_SCREEN_TRANSLATE",
            "Таймер 30 мин" to "QUICK_TIMER_30"
        )
        quickActions.forEach { (label, tag) ->
            val chip = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding((12 * density).toInt(), (7 * density).toInt(), (12 * density).toInt(), (7 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 18 * density
                    setColor(containerArgb)
                    setStroke((1 * density).toInt(), accentArgb)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = (8 * density).toInt()
                }
                setOnClickListener {
                    handleMiniQuickChip(tag, outputText)
                }
            }
            quickChipRow.addView(chip)
        }
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(quickChipRow)
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val input = EditText(this).apply {
            hint = "Команда или вопрос..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val sendBtn = Button(this).apply {
            text = "→"
            setTextColor(onAccentArgb)
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(accentArgb)
            }
            setOnClickListener {
                val q = input.text.toString().trim()
                if (q.isEmpty()) return@setOnClickListener
                input.setText("")
                handleOverlayQuery(q, outputText)
            }
        }
        inputRow.addView(input)
        inputRow.addView(sendBtn)

        root.addView(headerRow)
        root.addView(scroll)
        root.addView(chipScroll)
        root.addView(inputRow)

        val params = WindowManager.LayoutParams(
            (320 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutFlag(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        panelView = root
        try {
            windowManager?.addView(root, params)
        } catch (e: Exception) {
            panelView = null
            JarvisCrashReporter.recordNonFatal(e, "overlay_add_panel_view")
        }
    }

    private fun handleMiniQuickChip(tag: String, outputText: TextView) {
        when (tag) {
            "QUICK_MEDIA_PLAY_PAUSE" -> handleOverlayQuery("пауза", outputText)
            "QUICK_MEDIA_NEXT" -> handleOverlayQuery("следующий трек", outputText)
            "QUICK_TIMER_30" -> handleOverlayQuery("таймер 30 минут", outputText)
            "QUICK_VIDEO_SUMMARY" -> {
                val screenText = captureScreenSummaryText()
                val prompt = if (screenText.isNotBlank()) {
                    "Кратко перескажи суть видео / контента по элементам экрана:\n$screenText"
                } else {
                    "Дай шаблон краткого пересказа YouTube-видео и подскажи, как включить экранного агента для автозахвата названия."
                }
                streamMiniLlm(prompt, outputText)
            }
            "QUICK_GAME_GUIDE" -> {
                val screenText = captureScreenSummaryText()
                val prompt = if (screenText.isNotBlank()) {
                    "Помоги пройти текущий игровой этап без лишних спойлеров. Контекст экрана:\n$screenText"
                } else {
                    "Дай короткие тактические советы для прохождения сложного босса в игре (позиционирование, тайминги, ресурсы)."
                }
                streamMiniLlm(prompt, outputText)
            }
            "QUICK_SCREEN_TRANSLATE" -> {
                val screenText = captureScreenSummaryText()
                if (screenText.isBlank()) {
                    outputText.text = "Экранный агент (Accessibility) отключён или на экране нет текста. Включите «Экранный агент» в Настройках для перевода текста поверх игр/YouTube."
                } else {
                    streamMiniLlm(
                        "Переведи весь этот текст с экрана на русский язык (коротко и понятно):\n$screenText",
                        outputText
                    )
                }
            }
        }
    }

    private fun captureScreenSummaryText(): String {
        val svc = JarvisAccessibilityService.instance ?: return ""
        return svc.captureOnDemandSnapshot()
            .map { it.label.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
            .distinct()
            .take(30)
            .joinToString(" | ")
    }

    private fun handleOverlayQuery(query: String, outputText: TextView) {
        val lang = prefs.state.value.language
        when (val decision = router.route(query, lang)) {
            is RouteDecision.LocalHandled -> {
                outputText.text = decision.replyText
            }
            is RouteDecision.ConfirmationRequired -> {
                outputText.text = decision.replyText + "\n" + router.executeConfirmedAction(decision.actionPayloadJson, lang)
            }
            is RouteDecision.ForwardToLlm -> {
                streamMiniLlm(query, outputText)
            }
        }
    }

    private fun streamMiniLlm(prompt: String, outputText: TextView) {
        activeJob?.cancel()
        outputText.text = "..."
        activeJob = serviceScope.launch {
            val msg = MessageEntity(chatId = -99L, role = "user", content = prompt)
            llmClient.streamCompletion(
                history = listOf(msg),
                systemPrompt = "You are JARVIS Mini Overlay. Keep answers super concise and helpful.",
                reasoningEffort = "low",
                temperature = prefs.state.value.resolveTemperatureFloat()
            ).collect { ev ->
                when (ev) {
                    is StreamEvent.TokenDelta -> outputText.text = ev.accumulatedText
                    is StreamEvent.Completed -> outputText.text = ev.fullText
                    is StreamEvent.Error -> outputText.text = if (ev.message == "NO_API_KEY") {
                        "API-ключ не задан в приложении JARVIS."
                    } else ev.message
                    is StreamEvent.FailoverNotice -> outputText.text = ev.message
                }
            }
        }
    }

    private fun cleanupOverlayViews() {
        activeJob?.cancel()
        activeJob = null
        panelView?.let { v ->
            if (v.isAttachedToWindow) runCatching { windowManager?.removeViewImmediate(v) }
        }
        panelView = null
        dismissView?.let { v ->
            if (v.isAttachedToWindow) runCatching { windowManager?.removeViewImmediate(v) }
        }
        dismissView = null
        bubbleView?.let { v ->
            if (v.isAttachedToWindow) runCatching { windowManager?.removeViewImmediate(v) }
        }
        bubbleView = null
    }

    override fun onDestroy() {
        JarvisCrashReporter.setOverlayActive(false)
        cleanupOverlayViews()
        serviceScope.cancel()
        windowManager = null
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        serviceViewModelStore.clear()
        super.onDestroy()
    }
}
