package com.janreins.tonnage.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.data.model.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Small copies of each exercise's first picture for lists, decoded at half size and kept in a
 * bounded cache so scrolling the library doesn't decode the same file again.
 */
object ExerciseThumbnails {
    private const val CACHE_BYTES = 12 * 1024 * 1024

    /** Ids known to have no picture, so they aren't looked up again. */
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(exerciseId: String): ImageBitmap? = cache.get(exerciseId)?.asImageBitmap()

    /** The first picture at half size, or null for custom exercises and built-ins without one. Call off the main thread. */
    fun load(assets: AssetManager, exercise: Exercise): ImageBitmap? {
        if (exercise.isCustom || exercise.id in missing) return null
        cache.get(exercise.id)?.let { return it.asImageBitmap() }
        val bitmap = try {
            assets.open(ExerciseImages.paths(exercise.id).first()).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 })
            }
        } catch (_: IOException) {
            null
        }
        if (bitmap == null) {
            missing += exercise.id
            return null
        }
        cache.put(exercise.id, bitmap)
        return bitmap.asImageBitmap()
    }
}

/** An exercise's picture as a small rounded square, or a dumbbell icon when it has none. */
@Composable
fun ExerciseThumbnail(exercise: Exercise?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val assets = LocalContext.current.assets
    val image by produceState<ImageBitmap?>(exercise?.let { ExerciseThumbnails.cached(it.id) }, exercise?.id, exercise?.isCustom) {
        value = exercise?.let { ExerciseThumbnails.cached(it.id) ?: withContext(Dispatchers.IO) { ExerciseThumbnails.load(assets, it) } }
    }
    val shape = RoundedCornerShape(10.dp)
    val shown = image
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).background(Color.White),
        )
    } else {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}
