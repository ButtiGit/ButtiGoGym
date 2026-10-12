package it.pulse.workout.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.PathParser
import it.pulse.workout.data.MuscleGroup
import it.pulse.workout.data.MuscleProgress
import it.pulse.workout.data.MuscleRecovery
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Native Android version of the MIT-licensed MuscleMap anatomy.
 *
 * Unlike a WebView, the photograph is drawn directly by Android Canvas and
 * every actual MuscleMap SVG path is decoded by AndroidX PathParser.
 * No HTML, JavaScript, network, or hand-drawn anatomical approximation.
 */
internal class NativeMuscleMapView(context: Context) : View(context) {
    private data class Part(
        val muscle: String,
        val shape: Path,
        val touchRegion: Region
    )

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4.2f
    }
    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(37, 67, 47)
        textSize = 27f
    }
    private val bitmaps: Map<String, Bitmap?> = mapOf(
        "front" to loadBitmap("male-front.webp"),
        "back" to loadBitmap("male-back.webp")
    )

    private val paths: Map<String, List<Part>> = runCatching {
        val json = context.assets.open("muscle-map-regions.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val views = JSONObject(json).getJSONObject("views")
        listOf("front", "back").associateWith { side ->
            val array = views.getJSONArray(side)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.getString("muscle")
                    if (id !in MuscleGroup.ids) continue
                    val shape = PathParser.createPathFromPathData(item.getString("path"))
                    val region = Region().apply {
                        setPath(shape, Region(0, 0, 1024, 1536))
                    }
                    add(Part(id, shape, region))
                }
            }
        }
    }.getOrDefault(emptyMap())

    private var side = "front"
    private var selected = "chest"
    private var states: Map<String, MuscleRecovery> = emptyMap()
    private var onSelected: (String) -> Unit = {}

    // The original source images/path data share a 1024 x 1536 viewBox.
    // Crop the large white lateral margins without distorting the anatomy.
    private val viewport = RectF(160f, 20f, 864f, 1460f)
    private var lastScale = 1f
    private var lastOffsetX = 0f
    private var lastOffsetY = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Mappa anatomica dei muscoli, fronte e retro; tocca un muscolo per selezionarlo"
    }

    private fun loadBitmap(name: String): Bitmap? =
        runCatching { context.assets.open(name).use { BitmapFactory.decodeStream(it) } }.getOrNull()

    fun bind(
        showBack: Boolean,
        progress: List<MuscleProgress>,
        selectedMuscle: String,
        selectedCallback: (String) -> Unit
    ) {
        side = if (showBack) "back" else "front"
        states = progress.associate { it.group.id to it.state }
        selected = selectedMuscle
        onSelected = selectedCallback
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val zoom = min(w / viewport.width(), h / viewport.height())
        lastScale = zoom
        lastOffsetX = (w - viewport.width() * zoom) / 2f
        lastOffsetY = (h - viewport.height() * zoom) / 2f

        val checkpoint = canvas.save()
        canvas.translate(lastOffsetX, lastOffsetY)
        canvas.scale(zoom, zoom)
        canvas.translate(-viewport.left, -viewport.top)
        canvas.clipRect(viewport)

        val image = bitmaps[side]
        if (image != null && !image.isRecycled) {
            canvas.drawBitmap(
                image, null,
                RectF(0f, 0f, 1024f, 1536f),
                bodyPaint
            )
        } else {
            canvas.drawText("Immagine anatomica non disponibile", 170f, 700f, errorPaint)
        }

        for (part in paths[side].orEmpty()) {
            val state = states[part.muscle] ?: MuscleRecovery.NEVER
            overlayPaint.color = when (state) {
                MuscleRecovery.NEVER -> Color.argb(28, 180, 198, 185)
                MuscleRecovery.TRAINED_TODAY -> Color.argb(185, 16, 97, 25)
                MuscleRecovery.RECOVERING -> Color.argb(185, 255, 178, 69)
                MuscleRecovery.READY -> Color.argb(147, 141, 211, 154)
            }
            canvas.drawPath(part.shape, overlayPaint)
            strokePaint.color = if (selected == part.muscle) {
                Color.rgb(7, 81, 28)
            } else {
                Color.argb(110, 245, 250, 246)
            }
            strokePaint.strokeWidth = if (selected == part.muscle) 7f else 2.1f
            canvas.drawPath(part.shape, strokePaint)
        }
        canvas.restoreToCount(checkpoint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        performClick()
        if (lastScale <= 0f) return true
        val x = ((event.x - lastOffsetX) / lastScale + viewport.left).roundToInt()
        val y = ((event.y - lastOffsetY) / lastScale + viewport.top).roundToInt()
        val hit = paths[side].orEmpty().asReversed().firstOrNull { part ->
            part.touchRegion.contains(x, y)
        }
        if (hit != null) {
            selected = hit.muscle
            invalidate()
            onSelected(hit.muscle)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
