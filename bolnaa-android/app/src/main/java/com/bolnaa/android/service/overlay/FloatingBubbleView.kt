package com.bolnaa.android.service.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.bolnaa.android.R
import com.bolnaa.android.data.models.DictationState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    var layoutParamsWindowManager: WindowManager.LayoutParams? = null

    // State
    var state: DictationState = DictationState.IDLE
        set(value) {
            field = value
            onStateChanged(value)
            invalidate()
        }

    var onBubbleClick: (() -> Unit)? = null
    var onCancelClick: (() -> Unit)? = null

    // Dimensions
    private val density = resources.displayMetrics.density
    private var baseSize = (72 * density).toInt()
    private var expandedWidth = (168 * density).toInt()

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#181824")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6366F1")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B5CF6")
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12 * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Live audio amplitudes for visualizer bars
    private val waveBars = FloatArray(7) { 0.2f }
    private var targetAmplitude = 0.1f
    private var pulsePhase = 0f

    // Drag tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val touchSlop = 10 * density

    // Animations
    private var expansionProgress = 0f // 0 = circle, 1 = expanded pill
    private var expandAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    init {
        setupAnimators()
    }

    private fun setupAnimators() {
        pulseAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulsePhase = it.animatedValue as Float
                if (state == DictationState.LISTENING || state == DictationState.PROCESSING) {
                    updateWaveBars()
                    invalidate()
                }
            }
        }
        pulseAnimator?.start()
    }

    fun setLiveAmplitude(amplitude: Float) {
        targetAmplitude = amplitude.coerceIn(0.05f, 1.0f)
    }

    fun updateBubbleSize(newSizeDp: Int) {
        val safeSize = newSizeDp.coerceIn(40, 90)
        baseSize = (safeSize * density).toInt()
        expandedWidth = (safeSize * 2.5f * density).toInt()
        layoutParamsWindowManager?.let { params ->
            val currentProgress = expansionProgress
            params.width = (baseSize + (expandedWidth - baseSize) * currentProgress).toInt()
            params.height = baseSize
            try {
                windowManager.updateViewLayout(this, params)
            } catch (e: Exception) {
                // Ignore layout race
            }
        }
        requestLayout()
        invalidate()
    }

    fun showAnimated() {
        if (visibility == View.VISIBLE && alpha == 1f) return
        visibility = View.VISIBLE
        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hideAnimated(onComplete: (() -> Unit)? = null) {
        if (visibility == View.GONE) return
        animate()
            .alpha(0f)
            .scaleX(0.6f)
            .scaleY(0.6f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                visibility = View.GONE
                onComplete?.invoke()
            }
            .start()
    }

    private fun updateWaveBars() {
        for (i in waveBars.indices) {
            val offset = i * 0.8f
            val baseSine = (sin(pulsePhase + offset) + 1f) / 2f
            val target = (baseSine * 0.3f + targetAmplitude * 0.7f).coerceIn(0.15f, 1.0f)
            waveBars[i] += (target - waveBars[i]) * 0.35f
        }
    }

    private fun onStateChanged(newState: DictationState) {
        expandAnimator?.cancel()
        val targetProgress = if (newState == DictationState.LISTENING || newState == DictationState.PROCESSING) 1f else 0f

        expandAnimator = ValueAnimator.ofFloat(expansionProgress, targetProgress).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                expansionProgress = it.animatedValue as Float
                requestLayout()
                invalidate()
            }
        }
        expandAnimator?.start()

        triggerHaptic(newState)
    }

    fun triggerHaptic(dictState: DictationState) {
        if (vibrator == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (dictState) {
                    DictationState.LISTENING -> vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                    DictationState.SUCCESS -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 60), -1))
                    DictationState.ERROR -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 80), -1))
                    else -> {}
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val currentWidth = (baseSize + (expandedWidth - baseSize) * expansionProgress).toInt()
        val currentHeight = baseSize
        setMeasuredDimension(currentWidth, currentHeight)

        // Update WindowManager layout width dynamically
        layoutParamsWindowManager?.let { params ->
            if (params.width != currentWidth || params.height != currentHeight) {
                params.width = currentWidth
                params.height = currentHeight
                try {
                    windowManager.updateViewLayout(this, params)
                } catch (e: Exception) {
                    // Ignore during teardown
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        // Squircle corner radius matching Bolnaa app logo (rounded square)
        val cornerRadius = if (expansionProgress > 0f) {
            (h * 0.28f) + ((h / 2f) - (h * 0.28f)) * expansionProgress
        } else {
            h * 0.28f
        }

        // 1. Draw Glassmorphic Squircle Background with Gradient
        val bgGradient = when (state) {
            DictationState.LISTENING -> LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.parseColor("#881337"), Color.parseColor("#E11D48")),
                null, Shader.TileMode.CLAMP
            )
            DictationState.PROCESSING -> LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.parseColor("#312E81"), Color.parseColor("#6366F1")),
                null, Shader.TileMode.CLAMP
            )
            DictationState.SUCCESS -> LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.parseColor("#064E3B"), Color.parseColor("#10B981")),
                null, Shader.TileMode.CLAMP
            )
            DictationState.ERROR -> LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.parseColor("#450A0A"), Color.parseColor("#EF4444")),
                null, Shader.TileMode.CLAMP
            )
            else -> LinearGradient(
                0f, 0f, w, h,
                intArrayOf(Color.parseColor("#4338CA"), Color.parseColor("#7C3AED")),
                null, Shader.TileMode.CLAMP
            )
        }

        backgroundPaint.shader = bgGradient

        when (state) {
            DictationState.LISTENING -> borderPaint.color = Color.parseColor("#FDA4AF")
            DictationState.PROCESSING -> borderPaint.color = Color.parseColor("#C7D2FE")
            DictationState.SUCCESS -> borderPaint.color = Color.parseColor("#6EE7B7")
            DictationState.ERROR -> borderPaint.color = Color.parseColor("#FCA5A5")
            else -> borderPaint.color = Color.parseColor("#A5B4FC")
        }

        val strokeOffset = borderPaint.strokeWidth / 2f
        val rectF = RectF(strokeOffset, strokeOffset, w - strokeOffset, h - strokeOffset)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, backgroundPaint)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

        // 2. Draw Content based on state
        when (state) {
            DictationState.IDLE -> drawIdleState(canvas, w, h)
            DictationState.LISTENING -> drawListeningState(canvas, w, h)
            DictationState.PROCESSING -> drawProcessingState(canvas, w, h)
            DictationState.SUCCESS -> drawSuccessState(canvas, w, h)
            DictationState.ERROR -> drawErrorState(canvas, w, h)
        }
    }

    private fun drawIdleState(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f

        // Draw Bolnaa Mic + Waveform silhouette matching app icon
        val whiteFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
            strokeCap = Paint.Cap.ROUND
        }

        // Center mic capsule
        val micWidth = 6.5f * density
        val micHeight = 13f * density
        val micRect = RectF(cx - micWidth / 2, cy - micHeight / 2 - 2 * density, cx + micWidth / 2, cy + micHeight / 2 - 2 * density)
        canvas.drawRoundRect(micRect, micWidth / 2, micWidth / 2, whiteFill)

        // Mic U-cradle
        val cradleRect = RectF(cx - 7.5f * density, cy - 7.5f * density, cx + 7.5f * density, cy + 7.5f * density)
        canvas.drawArc(cradleRect, 0f, 180f, false, whiteStroke)

        // Mic stand
        canvas.drawLine(cx, cy + 7.5f * density, cx, cy + 11.5f * density, whiteStroke)

        // Left audio sound wave bar
        canvas.drawLine(cx - 12f * density, cy - 4f * density, cx - 12f * density, cy + 4f * density, whiteStroke)

        // Right audio sound wave bar
        canvas.drawLine(cx + 12f * density, cy - 4f * density, cx + 12f * density, cy + 4f * density, whiteStroke)
    }

    private fun drawListeningState(canvas: Canvas, w: Float, h: Float) {
        val cy = h / 2f

        // Draw 7 dynamic audio visualizer bars in center
        val barCount = waveBars.size
        val barWidth = 4f * density
        val barSpacing = 5f * density
        val totalVisualizerWidth = (barCount * barWidth) + ((barCount - 1) * barSpacing)
        var startX = (w - totalVisualizerWidth) / 2f

        wavePaint.color = Color.parseColor("#F43F5E")
        val maxBarHeight = 24f * density

        for (i in 0 until barCount) {
            val barH = max(4f * density, waveBars[i] * maxBarHeight)
            val barRect = RectF(startX, cy - barH / 2, startX + barWidth, cy + barH / 2)
            canvas.drawRoundRect(barRect, barWidth / 2, barWidth / 2, wavePaint)
            startX += barWidth + barSpacing
        }
    }

    private fun drawProcessingState(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f

        textPaint.color = Color.parseColor("#A5B4FC")
        canvas.drawText("Flowing...", cx, cy + 4 * density, textPaint)
    }

    private fun drawSuccessState(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f

        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path().apply {
            moveTo(cx - 8f * density, cy)
            lineTo(cx - 2f * density, cy + 6f * density)
            lineTo(cx + 8f * density, cy - 6f * density)
        }
        canvas.drawPath(path, checkPaint)
    }

    private fun drawErrorState(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        textPaint.color = Color.parseColor("#F87171")
        canvas.drawText("Retry", cx, cy + 4 * density, textPaint)
    }

    var isFreePlacementEnabled: Boolean = true
    var onPositionChanged: ((Int, Int) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val params = layoutParamsWindowManager ?: return super.onTouchEvent(event)
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (!isDragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) {
                    params.x = (initialX + dx).toInt().coerceIn(0, (displayWidth - width).coerceAtLeast(0))
                    params.y = (initialY + dy).toInt().coerceIn(0, (displayHeight - height).coerceAtLeast(0))
                    try {
                        windowManager.updateViewLayout(this, params)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // Click triggered
                    performClick()
                    onBubbleClick?.invoke()
                } else {
                    if (isFreePlacementEnabled) {
                        val finalX = params.x.coerceIn(8, (displayWidth - width - 8).coerceAtLeast(8))
                        val finalY = params.y.coerceIn(32, (displayHeight - height - 32).coerceAtLeast(32))
                        params.x = finalX
                        params.y = finalY
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (e: Exception) {
                            // Ignore
                        }
                        onPositionChanged?.invoke(finalX, finalY)
                    } else {
                        snapToEdge(params)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val displayWidth = resources.displayMetrics.widthPixels
        val middle = displayWidth / 2
        val targetX = if (params.x + width / 2 < middle) 16 else displayWidth - width - 16

        val animator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                params.x = it.animatedValue as Int
                try {
                    windowManager.updateViewLayout(this@FloatingBubbleView, params)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        animator.start()
        onPositionChanged?.invoke(targetX, params.y)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        expandAnimator?.cancel()
    }
}
