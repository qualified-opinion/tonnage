package com.janreins.tonnage

import com.janreins.tonnage.data.backup.BackupContents
import com.janreins.tonnage.data.backup.BackupWorkout
import com.janreins.tonnage.data.backup.WorkoutCsv
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class WorkoutCsvTest {

    private val zone = ZoneOffset.UTC
    private val library = listOf(
        Exercise(id = "bench", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest"),
        Exercise(id = "pullup", name = "Pullups", category = "strength", equipment = "body only", primaryMuscles = "lats", trackingType = TrackingType.BODYWEIGHT_REPS),
        Exercise(id = "plank", name = "Plank", category = "strength", equipment = "body only", primaryMuscles = "abdominals", trackingType = TrackingType.DURATION),
        Exercise(id = "run", name = "Running", category = "cardio", equipment = "none", primaryMuscles = "quadriceps", trackingType = TrackingType.DISTANCE_DURATION),
    ).associateBy { it.id }

    private fun at(text: String) = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun workout(id: Long, title: String, start: String, vararg exercises: Pair<String, List<WorkoutSet>>) = BackupWorkout(
        Workout(id = id, title = title, startedAt = at(start), endedAt = at(start) + 3_600_000),
        exercises.mapIndexed { index, (exerciseId, sets) ->
            WorkoutExercise(id = id * 10 + index, workoutId = id, exerciseId = exerciseId, position = index, restSeconds = 90) to sets
        },
    )

    private fun set(position: Int, weight: Double? = null, reps: Int? = null, km: Double? = null, seconds: Int? = null, type: SetType = SetType.NORMAL) =
        WorkoutSet(workoutExerciseId = 0, position = position, type = type, weightKg = weight, reps = reps, distanceKm = km, durationSec = seconds, completed = true)

    private fun csv(vararg workouts: BackupWorkout) = WorkoutCsv.write(
        BackupContents(exportedAt = 0, defaultRestSeconds = 90, customExercises = emptyList(), workouts = workouts.toList(), routines = emptyList(), hevyMappings = emptyList()),
        library,
        zone,
    )

    private fun lines(text: String) = text.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun startsWithByteOrderMarkAndTheHeaderInOrder() {
        val text = csv()
        assertTrue(text.startsWith("﻿"))
        assertEquals(
            listOf("date,start_time,end_time,workout,exercise,set_number,set_type,weight_kg,reps,distance_km,duration_seconds,volume_kg"),
            lines(text),
        )
    }

    @Test
    fun weightSetsHaveVolumeAndWarmUpsAreLabelled() {
        val rows = lines(csv(workout(1, "Push", "2026-10-20T07:30", "bench" to listOf(set(0, 40.0, 10, type = SetType.WARMUP), set(1, 62.5, 8)))))
        assertEquals("2026-10-20,07:30,08:30,Push,Bench Press,1,Warm-up,40,10,,,", rows[1])
        assertEquals("2026-10-20,07:30,08:30,Push,Bench Press,2,Normal,62.5,8,,,500", rows[2])
    }

    @Test
    fun otherWaysOfLoggingLeaveUnusedCellsEmpty() {
        val rows = lines(csv(workout(1, "Mixed", "2026-10-20T07:30",
            "pullup" to listOf(set(0, weight = 5.0, reps = 12)),
            "plank" to listOf(set(0, seconds = 90)),
            "run" to listOf(set(0, km = 5.25, seconds = 1500)),
        )))
        assertEquals(",12,,,", rows[1].split(",").drop(7).joinToString(","))
        assertEquals(",,,90,", rows[2].split(",").drop(7).joinToString(","))
        assertEquals(",,5.25,1500,", rows[3].split(",").drop(7).joinToString(","))
    }

    @Test
    fun quotesCommasQuotesAndLineBreaks() {
        val text = csv(workout(1, "Legs, \"heavy\"\nday", "2026-10-20T07:30", "bench" to listOf(set(0, 60.0, 5))))
        assertTrue(text.contains(",\"Legs, \"\"heavy\"\"\nday\",Bench Press,"))
    }

    @Test
    fun formulaLikeTitlesArePrefixed() {
        listOf("=SUM(A1)", "+1", "-2", "@cmd").forEach { title ->
            val row = lines(csv(workout(1, title, "2026-10-20T07:30", "bench" to listOf(set(0, 60.0, 5)))))[1]
            assertTrue(row, row.contains(",'$title,"))
        }
    }

    @Test
    fun decimalPointIsADotUnderAGermanLocale() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val row = lines(csv(workout(1, "Push", "2026-10-20T07:30", "bench" to listOf(set(0, 1062.5, 3)))))[1]
            assertTrue(row, row.endsWith(",1062.5,3,,,3187.5"))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun rowsAreInDateOrderAndMissingExercisesAreNamed() {
        val rows = lines(csv(
            workout(2, "Later", "2026-10-21T07:30", "gone" to listOf(set(0, 50.0, 5))),
            workout(1, "Earlier", "2026-10-20T07:30", "bench" to listOf(set(1, 60.0, 5), set(0, 55.0, 5))),
        ))
        assertEquals(listOf("Earlier,Bench Press,1", "Earlier,Bench Press,2", "Later,Unknown exercise,1"), rows.drop(1).map { it.split(",").subList(3, 6).joinToString(",") })
        assertTrue(rows[1].contains(",55,5,"))
    }
}
