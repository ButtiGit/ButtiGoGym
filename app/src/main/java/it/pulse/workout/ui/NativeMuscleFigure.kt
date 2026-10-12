package it.pulse.workout.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import it.pulse.workout.data.MuscleGroup
import it.pulse.workout.data.MuscleProgress
import it.pulse.workout.data.MuscleRecovery
import org.json.JSONObject
import kotlin.math.roundToInt

/** Il corpo e i tracciati sono importati da Jsplice/MuscleMap (MIT), NON disegnati a mano. */
private data class NativeMuscleRegion(
    val muscle: String,
    val outline: Path,
    val hitBox: Region
)

private data class NativeAnatomy(
    val frontImage: Bitmap,
    val backImage: Bitmap,
    val frontRegions: List<NativeMuscleRegion>,
    val backRegions: List<NativeMuscleRegion>
)

private fun loadAnatomy(context: Context): NativeAnatomy {
    fun image(name: String): Bitmap = context.assets.open(name).use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
            inScaled = false
        }) ?: error("Invalid embedded anatomy photo: $name")
    }

    val manifest = context.assets.open("muscle-map.json").bufferedReader().use {
        JSONObject(it.readText())
    }
    val clip = Region(0, 0, 1024, 1536)

    fun parts(view: String): List<NativeMuscleRegion> {
        val array = manifest.getJSONArray(view)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val muscle = item.getString("muscle")
            require(muscle in MuscleGroup.ids) { "Invalid anatomy muscle: $muscle" }
            val path = PathParser.createPathFromPathData(item.getString("path"))
                ?: error("Unparseable muscle path $index")
            val hitRegion = Region()
            hitRegion.setPath(path, clip)
            NativeMuscleRegion(muscle, path, hitRegion)
        }.also {
            require(it.size >= 20) { "Incomplete anatomy map: $view" }
        }
    }

    return NativeAnatomy(
        frontImage = image("male-front.webp"),
        backImage = image("male-back.webp"),
        frontRegions = parts("front"),
        backRegions = parts("back")
    )
}

private data class AnatomyViewport(
    val scale: Float,
    val originX: Float,
    val originY: Float
) {
    /** The source artwork uses viewBox "160 20 704 1440". */
    fun toAnatomy(pos: Offset): Offset = Offset(
        (pos.x - originX) / scale + 160f,
        (pos.y - originY) / scale + 20f
    )
}

private fun viewport(width: Float, height: Float): AnatomyViewport {
    val scale = minOf(width / 704f, height / 1440f).coerceAtLeast(0.0001f)
    return AnatomyViewport(
        scale,
        (width - 704f * scale) / 2f,
        (height - 1440f * scale) / 2f
    )
}

private fun musclePaint(state: MuscleRecovery): Int = when (state) {
    MuscleRecovery.NEVER -> AndroidColor.argb(64, 218, 229, 220)
    MuscleRecovery.TRAINED_TODAY -> AndroidColor.argb(205, 16, 97, 25)
    MuscleRecovery.RECOVERING -> AndroidColor.argb(215, 250, 169, 73)
    MuscleRecovery.READY -> AndroidColor.argb(174, 113, 199, 124)
}

@Composable
internal fun MuscleFigure(
    back: Boolean,
    muscles: List<MuscleProgress>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val data = remember(context.applicationContext) {
        runCatching { loadAnatomy(context.applicationContext) }.onFailure {
            Log.e("ButtiGoGym/Myself", "Unable to load bundled MuscleMap assets", it)
        }
    }
    val anatomy = data.getOrNull()
    val states = remember(muscles) { muscles.associate { it.group.id to it.state } }
    val selectedHandler by rememberUpdatedState(onSelected)

    if (anatomy == null) {
        Box(
            modifier = modifier
                .background(PulseColors.Raised, RoundedCornerShape(16.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Mappa anatomica non disponibile",
                    color = PulseColors.Foreground, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Gli esercizi e le statistiche qui sotto sono comunque accessibili.",
                    color = PulseColors.Muted, fontSize = 12.sp)
            }
        }
        return
    }

    val regions = if (back) anatomy.backRegions else anatomy.frontRegions
    val body = if (back) anatomy.backImage else anatomy.frontImage

    Canvas(
        modifier = modifier
            .pointerInput(back, anatomy) {
                detectTapGestures { tap ->
                    val view = viewport(size.width.toFloat(), size.height.toFloat())
                    val coords = view.toAnatomy(tap)
                    if (coords.x !in 160f..864f || coords.y !in 20f..1460f) return@detectTapGestures
                    val selectedMuscle = regions.asReversed().firstOrNull {
                        it.hitBox.contains(coords.x.roundToInt(), coords.y.roundToInt())
                    }?.muscle
                    if (selectedMuscle != null) selectedHandler(selectedMuscle)
                }
            }
    ) {
        val target = viewport(size.width, size.height)
        drawIntoCanvas { composeCanvas ->
            val native = composeCanvas.nativeCanvas
            val saveCount = native.save()
            try {
                native.translate(target.originX, target.originY)
                native.scale(target.scale, target.scale)
                native.translate(-160f, -20f)

                // Render the licensed anatomical photo, desaturated to avoid
                // confusing its original colors with readiness/status colors.
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(gray)
                    alpha = 250
                }
                native.drawBitmap(body, 0f, 0f, photoPaint)

                val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                }

                regions.forEach { region ->
                    val isSelected = region.muscle == selected
                    surfacePaint.color = musclePaint(
                        states[region.muscle] ?: MuscleRecovery.NEVER
                    )
                    native.drawPath(region.outline, surfacePaint)

                    borderPaint.color = if (isSelected) {
                        AndroidColor.rgb(9, 76, 21)
                    } else {
                        AndroidColor.argb(165, 255, 255, 255)
                    }
                    borderPaint.strokeWidth = if (isSelected) 5.5f else 1.7f
                    native.drawPath(region.outline, borderPaint)
                }
            } finally {
                native.restoreToCount(saveCount)
            }
        }
    }
}
