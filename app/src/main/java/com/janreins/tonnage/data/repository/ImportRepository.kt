package com.janreins.tonnage.data.repository

import androidx.room.withTransaction
import com.janreins.tonnage.data.hevy.ExerciseMatch
import com.janreins.tonnage.data.hevy.ExerciseMatcher
import com.janreins.tonnage.data.hevy.HevyCsv
import com.janreins.tonnage.data.hevy.HevyExercise
import com.janreins.tonnage.data.hevy.HevyWorkout
import com.janreins.tonnage.data.hevy.HevyWorkoutIdentity
import com.janreins.tonnage.data.hevy.identity
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.HevyMapping
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.settings.SettingsStore
import java.util.TimeZone
import java.util.UUID

/** What a Hevy exercise name becomes in Tonnage. */
sealed interface ExerciseChoice {
    data class Existing(val exerciseId: String) : ExerciseChoice
    /** A new custom exercise with the Hevy name. */
    data object Custom : ExerciseChoice
}

/** A parsed Hevy file, checked against the library and what was imported before. Nothing is saved yet. */
data class ImportPlan(
    val workouts: List<HevyWorkout>,
    /** Workouts not imported before; only these are saved. */
    val newWorkouts: List<HevyWorkout>,
    /** Matches for every exercise name in [newWorkouts], in first-seen order. */
    val matches: List<ExerciseMatch>,
    /** Sets per exercise name, in [newWorkouts]. */
    val setCounts: Map<String, Int>,
    val timeZoneId: String,
) {
    val skipped: Int get() = workouts.size - newWorkouts.size
    val setCount: Int get() = newWorkouts.sumOf { w -> w.exercises.sumOf { it.sets.size } }
    val firstStartedAt: Long? get() = newWorkouts.minOfOrNull { it.startedAt }
    val lastStartedAt: Long? get() = newWorkouts.maxOfOrNull { it.startedAt }

    /** The starting choice for each name: the match if there is one, otherwise a custom exercise. */
    fun defaultChoices(): Map<String, ExerciseChoice> = matches.associate { match ->
        match.hevyName to (match.exerciseId?.let { ExerciseChoice.Existing(it) } ?: ExerciseChoice.Custom)
    }
}

data class ImportResult(val workouts: Int, val sets: Int, val skipped: Int, val createdExercises: Int)

class ImportRepository(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.importDao()

    suspend fun prepare(csv: String): ImportPlan {
        val zone = settings.hevyImportZone ?: TimeZone.getDefault().id
        val workouts = HevyCsv.parse(csv, TimeZone.getTimeZone(zone))
        val imported = importedIdentities()
        val newWorkouts = workouts.filter { it.identity !in imported }
        val saved = dao.mappings().associate { it.hevyName to it.exerciseId }
        val matcher = ExerciseMatcher(dao.allExercises(), saved)
        val exercises = newWorkouts.flatMap { it.exercises }
        return ImportPlan(
            workouts = workouts,
            timeZoneId = zone,
            newWorkouts = newWorkouts,
            matches = exercises.map { it.name }.distinct().map(matcher::match),
            setCounts = exercises.groupBy { it.name }.mapValues { (_, list) -> list.sumOf { it.sets.size } },
        )
    }

    /**
     * Saves the plan's new workouts in one transaction: creates the custom exercises chosen,
     * remembers every name's choice for next time, and adds the workouts as finished.
     */
    suspend fun import(plan: ImportPlan, choices: Map<String, ExerciseChoice>, restSeconds: Int): ImportResult {
        val result = db.withTransaction {
            val alreadyImported = importedIdentities().toMutableSet()
            val workouts = plan.newWorkouts.filter { alreadyImported.add(it.identity) }
            val exercisesByName = workouts.flatMap { it.exercises }.groupBy { it.name }

            val created = mutableListOf<Exercise>()
            val idByName = exercisesByName.keys.associateWith { name ->
                when (val choice = choices[name] ?: ExerciseChoice.Custom) {
                    is ExerciseChoice.Existing -> choice.exerciseId
                    ExerciseChoice.Custom -> {
                        val exercise = Exercise(
                            id = "custom-" + UUID.randomUUID(),
                            name = name,
                            category = "custom",
                            equipment = "other",
                            primaryMuscles = "",
                            trackingType = trackingTypeFor(exercisesByName.getValue(name)),
                            isCustom = true,
                            createdAt = now(),
                        )
                        created += exercise
                        exercise.id
                    }
                }
            }
            if (created.isNotEmpty()) dao.insertExercises(created)
            dao.saveMappings(idByName.map { (name, id) -> HevyMapping(ExerciseMatcher.normalizeName(name), id) })

            val workoutDao = db.workoutDao()
            workouts.forEach { workout ->
                workoutDao.insertWorkoutWithContents(
                    Workout(
                        title = workout.identity.title,
                        startedAt = workout.startedAt,
                        endedAt = workout.endedAt,
                        notes = workout.description,
                        source = Workout.SOURCE_HEVY,
                    ),
                    workout.exercises.mapIndexed { index, exercise ->
                        WorkoutExercise(
                            workoutId = 0,
                            exerciseId = idByName.getValue(exercise.name),
                            position = index,
                            restSeconds = restSeconds,
                            notes = exercise.notes,
                        ) to exercise.sets.mapIndexed { setIndex, set ->
                            WorkoutSet(
                                workoutExerciseId = 0,
                                position = setIndex,
                                type = set.type,
                                weightKg = set.weightKg,
                                reps = set.reps,
                                distanceKm = set.distanceKm,
                                durationSec = set.durationSec,
                                rpe = set.rpe,
                                completed = true,
                                completedAt = workout.endedAt,
                            )
                        }
                    },
                )
            }
            ImportResult(
                workouts = workouts.size,
                sets = workouts.sumOf { w -> w.exercises.sumOf { it.sets.size } },
                skipped = plan.workouts.size - workouts.size,
                createdExercises = created.size,
            )
        }
        if (plan.workouts.isNotEmpty()) settings.rememberHevyImportZone(plan.timeZoneId)
        return result
    }

    private suspend fun importedIdentities(): Set<HevyWorkoutIdentity> =
        dao.hevyWorkouts().map { HevyWorkoutIdentity.of(it.title, it.startedAt) }.toSet()

    companion object {
        /** Guesses how a new exercise is logged from the numbers Hevy recorded for it. */
        fun trackingTypeFor(exercises: List<HevyExercise>): TrackingType {
            val sets = exercises.flatMap { it.sets }
            return when {
                sets.any { (it.distanceKm ?: 0.0) > 0.0 } -> TrackingType.DISTANCE_DURATION
                sets.any { (it.weightKg ?: 0.0) > 0.0 } -> TrackingType.WEIGHT_REPS
                sets.any { (it.reps ?: 0) > 0 } -> TrackingType.BODYWEIGHT_REPS
                sets.any { (it.durationSec ?: 0) > 0 } -> TrackingType.DURATION
                else -> TrackingType.WEIGHT_REPS
            }
        }
    }
}
