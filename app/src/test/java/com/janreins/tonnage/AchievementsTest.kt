package com.janreins.tonnage

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.ProgressSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.AchievementKind
import com.janreins.tonnage.domain.Achievements
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.MilestoneKind
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.domain.PrKind
import com.janreins.tonnage.domain.WorkoutStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AchievementsTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: String): Long = LocalDate.parse(date).atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun past(workoutId: Long, date: String, exerciseId: String, weight: Double, reps: Int) = ProgressSet(
        workoutId, at(date), exerciseId, exerciseId.replace('_', ' '), TrackingType.WEIGHT_REPS.name, "chest", weight, reps, null, null,
    )

    private val bench = Exercise(id = "Barbell_Bench_Press_-_Medium_Grip", name = "Bench Press", category = "strength", equipment = "barbell", primaryMuscles = "chest")
    private val fly = Exercise(id = "Cable_Fly", name = "Cable Fly", category = "strength", equipment = "cable", primaryMuscles = "chest")

    private fun block(exercise: Exercise, id: Long, vararg sets: WorkoutSet) = ExerciseBlock(
        WorkoutExercise(id = id, workoutId = 9, exerciseId = exercise.id, position = id.toInt(), restSeconds = 90),
        exercise,
        sets.toList(),
    )

    private fun done(position: Int, weight: Double, reps: Int, type: SetType = SetType.NORMAL) =
        WorkoutSet(id = position.toLong(), workoutExerciseId = 1, position = position, type = type, weightKg = weight, reps = reps, completed = true)

    @Test
    fun onlyTickedWorkingSetsCountTowardsTheWorkout() {
        val blocks = listOf(
            block(
                bench, 1,
                done(0, 40.0, 10, SetType.WARMUP),
                done(1, 60.0, 5),
                WorkoutSet(id = 3, workoutExerciseId = 1, position = 2, weightKg = 60.0, reps = 5),
                WorkoutSet(id = 4, workoutExerciseId = 1, position = 3, completed = true),
            ),
        )
        val sets = Achievements.progressSets(9, at("2026-06-15"), blocks)
        assertEquals(1, sets.size)
        assertEquals(60.0, sets.single().weightKg!!, 0.0)
        assertEquals("chest", sets.single().primaryMuscles)
    }

    @Test
    fun aWorkoutShowsItsRecordsMilestonesBestsAndNewExercises() {
        // Four earlier workouts of 1,000 kg of bench each; this one does 6 × 100 kg × 5 and a new fly.
        val history = (1..4).map { WorkoutStart(it.toLong(), at("2026-06-0$it")) }
        val historySets = history.flatMap { w -> (1..2).map { past(w.workoutId, LocalDate.ofEpochDay(w.startedAt / 86_400_000).toString(), bench.id, 100.0, 5) } }
        val workout = WorkoutStart(9, at("2026-06-15"))
        val blocks = listOf(
            block(bench, 1, *(1..6).map { done(it, 100.0, 5) }.toTypedArray()),
            block(fly, 2, done(1, 20.0, 12)),
        )
        val sets = Achievements.progressSets(workout.workoutId, workout.startedAt, blocks)
        val record = PersonalRecord(bench.id, "Bench Press", PrKind.HEAVIEST_WEIGHT, 105.0, 100.0)

        val achievements = Achievements.forWorkout(workout, sets, history, historySets, listOf(record), now = at("2026-06-15"), zone = zone)

        // 6 × 100 kg × 5 + 20 kg × 12 = 3,240 kg: a pickup truck, shown first.
        assertEquals("You lifted about a pickup truck", achievements[0].title)
        assertEquals(AchievementKind.RECORD, achievements[1].kind)
        assertEquals("Heaviest weight 105 kg (was 100 kg)", achievements[1].detail)
        val milestones = achievements.filter { it.kind == AchievementKind.MILESTONE }
        // The fifth workout; earlier steps (first workout, 4 in a month) were reached before it.
        assertTrue(milestones.any { it.title == "5 workouts" && it.milestoneKind == MilestoneKind.WORKOUTS })
        assertTrue(milestones.none { it.title == "First workout" || it.title == "4 workouts in a month" })
        assertEquals(
            listOf("Biggest workout yet", "Most sets in one workout"),
            achievements.filter { it.kind == AchievementKind.WORKOUT_BEST }.map { it.title },
        )
        assertEquals(listOf("Cable Fly"), achievements.filter { it.kind == AchievementKind.NEW_EXERCISE }.map { it.title })
    }

    @Test
    fun aFirstWorkoutOnlyGetsItsMilestones() {
        val workout = WorkoutStart(1, at("2026-06-15"))
        val sets = Achievements.progressSets(1, workout.startedAt, listOf(block(bench, 1, done(1, 60.0, 4))))
        val achievements = Achievements.forWorkout(workout, sets, emptyList(), emptyList(), emptyList(), now = at("2026-06-15"), zone = zone)

        assertEquals("You lifted about a motorbike", achievements.first().title)
        assertTrue(achievements.drop(1).all { it.kind == AchievementKind.MILESTONE })
        assertTrue(achievements.any { it.title == "First workout" })
        assertTrue(achievements.any { it.title == "Bench press 60 kg" })
    }

    @Test
    fun theTotalIsShownAsTheHeaviestThingItReaches() {
        assertEquals(null, Achievements.equivalent(0.0))
        assertEquals(null, Achievements.equivalent(3.5))
        assertEquals("You lifted about a house cat", Achievements.equivalent(4.0)?.title)
        // Up to 1.5 of a thing reads as one; beyond that it's counted.
        assertEquals("You lifted about a school bus", Achievements.equivalent(17_000.0)?.title)
        assertEquals("You lifted about 2 pickup trucks", Achievements.equivalent(5_200.0)?.title)
        val bus = Achievements.equivalent(12_340.0)!!
        assertEquals(AchievementKind.EQUIVALENT, bus.kind)
        assertEquals("12,340 kg in total; a school bus weighs about 12,000 kg", bus.detail)
        assertEquals("You lifted about 2 jumbo jets", Achievements.equivalent(400_000.0)?.title)
        // Lightest first, so the pick is always the heaviest thing reached.
        assertEquals(Achievements.THINGS.sortedBy { it.kg }, Achievements.THINGS)
    }
}
