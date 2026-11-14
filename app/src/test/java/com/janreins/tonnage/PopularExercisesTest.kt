package com.janreins.tonnage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.ExerciseSeed
import com.janreins.tonnage.ui.exercises.PopularExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PopularExercisesTest {

    @Test
    fun everyPopularExerciseIsInTheLibraryOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ids = ExerciseSeed.parse(context.assets.open(ExerciseSeed.ASSET_NAME).bufferedReader().use { it.readText() })
            .map { it.id }.toSet()
        val missing = PopularExercises.ids.filterNot { it in ids }
        assertTrue("Unknown ids: $missing", missing.isEmpty())
        assertEquals(PopularExercises.ids.size, PopularExercises.ids.toSet().size)
    }
}
