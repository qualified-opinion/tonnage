package com.janreins.tonnage

import android.content.Context
import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.ui.components.ExerciseImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ExerciseImagesTest {

    private val assets: AssetManager by lazy { ApplicationProvider.getApplicationContext<Context>().assets }
    private val library by lazy {
        ExerciseSeed.parse(assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() })
    }

    /** free-exercise-db has no photos for these; the detail screen shows a placeholder. */
    private val withoutPictures = setOf(
        "Kettlebell_Halo",
        "Kettlebell_Halo_With_Overhead_Extension",
        "Kettlebell_Overhead_Triceps_Extension",
    )

    private fun header(path: String): ByteArray? = try {
        assets.open(path).use { stream -> ByteArray(12).also { stream.read(it) } }
    } catch (_: IOException) {
        null
    }

    @Test
    fun everyBuiltInExerciseHasBothPicturesAsWebP() {
        val missing = mutableSetOf<String>()
        library.forEach { exercise ->
            ExerciseImages.paths(exercise.id).forEach { path ->
                val bytes = header(path)
                if (bytes == null) {
                    missing += exercise.id
                } else {
                    assertTrue("$path is not a WebP", String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP")
                }
            }
        }
        assertEquals(withoutPictures, missing)
    }

    @Test
    fun noPicturesForExercisesThatAreNotInTheLibrary() {
        val folders = assets.list(ExerciseImages.FOLDER).orEmpty().toSet()
        assertEquals(emptySet<String>(), folders - library.map { it.id }.toSet())
    }

    @Test
    fun loadsBothPicturesForABuiltIn() {
        val squat = library.first { it.id == "Barbell_Squat" }
        assertEquals(2, ExerciseImages.load(assets, squat).size)
    }

    @Test
    fun customAndMissingExercisesGetNoPictures() {
        val custom = Exercise(id = "Barbell_Squat", name = "Mine", category = "strength", equipment = "barbell", primaryMuscles = "quadriceps", isCustom = true)
        assertEquals(0, ExerciseImages.load(assets, custom).size)
        val halo = library.first { it.id == "Kettlebell_Halo" }
        assertEquals(0, ExerciseImages.load(assets, halo).size)
    }
}
