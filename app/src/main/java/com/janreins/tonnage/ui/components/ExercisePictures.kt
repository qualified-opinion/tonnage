package com.janreins.tonnage.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Photos of built-in exercises, bundled from free-exercise-db (public domain) by
 * `tools/exercise-images/convert.py`: `exercise_images/<id>/0.webp` and `1.webp`, two positions
 * of the movement. A few built-ins and every custom exercise have none.
 */
object ExerciseImages {
    const val FOLDER = "exercise_images"
    const val FRAMES = 2

    fun paths(exerciseId: String): List<String> = (0 until FRAMES).map { "$FOLDER/$exerciseId/$it.webp" }

    /** Decodes the exercise's pictures that exist; empty when it has none. Call off the main thread. */
    fun load(assets: AssetManager, exercise: Exercise): List<ImageBitmap> {
        if (exercise.isCustom) return emptyList()
        return paths(exercise.id).mapNotNull { path ->
            try {
                assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } catch (_: IOException) {
                null
            }
        }
    }
}

private const val FRAME_MILLIS = 1200L

/**
 * Shows how the exercise is done: its two pictures take turns like a short animation (tap to
 * pause), or a placeholder when there are none.
 */
@Composable
fun ExercisePictures(exercise: Exercise, modifier: Modifier = Modifier) {
    val assets = LocalContext.current.assets
    val frames by produceState<List<ImageBitmap>?>(null, exercise.id, exercise.isCustom) {
        value = withContext(Dispatchers.IO) { ExerciseImages.load(assets, exercise) }
    }
    val loaded = frames ?: return
    val shape = RoundedCornerShape(12.dp)
    val boxModifier = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
    if (loaded.isEmpty()) {
        Row(
            boxModifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.ImageNotSupported, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (exercise.isCustom) "Custom exercises don't have a picture." else "No picture for this exercise.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    var index by remember(loaded) { mutableIntStateOf(0) }
    var playing by remember(loaded) { mutableStateOf(true) }
    LaunchedEffect(loaded, playing) {
        while (playing && loaded.size > 1) {
            delay(FRAME_MILLIS)
            index = (index + 1) % loaded.size
        }
    }
    Column(boxModifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clickable(enabled = loaded.size > 1, onClickLabel = if (playing) "Pause" else "Play") { playing = !playing },
        ) {
            Crossfade(targetState = index, label = "exercise picture") { shown ->
                Image(
                    bitmap = loaded[shown],
                    contentDescription = "${exercise.name}, picture ${shown + 1} of ${loaded.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (loaded.size > 1) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                loaded.indices.forEach { i ->
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }
        }
    }
}
