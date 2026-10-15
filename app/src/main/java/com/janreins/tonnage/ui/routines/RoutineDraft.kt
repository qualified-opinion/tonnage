package com.janreins.tonnage.ui.routines

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.RoutineContents
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.domain.ExerciseBlock

/** A planned set while editing; [key] identifies it on screen until the routine is saved. */
data class DraftSet(
    val key: Long,
    val type: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distanceKm: Double? = null,
    val durationSec: Int? = null,
)

data class DraftExercise(val key: Long, val exerciseId: String, val restSeconds: Int, val sets: List<DraftSet>)

/**
 * A routine being edited. It lives in memory until Save, so closing the editor leaves the
 * saved routine untouched. [id] is 0 for a routine that hasn't been saved yet.
 */
data class RoutineDraft(val id: Long = 0, val name: String = "", val exercises: List<DraftExercise> = emptyList()) {

    val canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty()

    fun addExercises(exerciseIds: List<String>, restSeconds: Int, nextKey: () -> Long) = copy(
        exercises = exercises + exerciseIds.map { DraftExercise(nextKey(), it, restSeconds, listOf(DraftSet(nextKey()))) },
    )

    fun removeExercise(key: Long) = copy(exercises = exercises.filterNot { it.key == key })

    /** Moves an exercise one place; [direction] is -1 (up) or +1 (down). */
    fun moveExercise(key: Long, direction: Int): RoutineDraft {
        val index = exercises.indexOfFirst { it.key == key }
        val target = index + direction
        if (index < 0 || target !in exercises.indices) return this
        return copy(exercises = exercises.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun updateExercise(key: Long, transform: (DraftExercise) -> DraftExercise) = copy(
        exercises = exercises.map { if (it.key == key) transform(it) else it },
    )

    /** Adds a set after the last one with the same numbers, as in the workout screen. */
    fun addSet(exerciseKey: Long, nextKey: () -> Long) = updateExercise(exerciseKey) { exercise ->
        val last = exercise.sets.lastOrNull()
        exercise.copy(sets = exercise.sets + (last?.copy(key = nextKey(), type = SetType.NORMAL) ?: DraftSet(nextKey())))
    }

    fun updateSet(setKey: Long, transform: (DraftSet) -> DraftSet) = copy(
        exercises = exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.key == setKey) transform(it) else it })
        },
    )

    fun deleteSet(setKey: Long) = copy(
        exercises = exercises.map { exercise -> exercise.copy(sets = exercise.sets.filterNot { it.key == setKey }) },
    )

    /** The rows to save; positions are assigned by the database layer from list order. */
    fun toEntities(): List<Pair<RoutineExercise, List<RoutineSet>>> = exercises.mapIndexed { index, exercise ->
        RoutineExercise(routineId = id, exerciseId = exercise.exerciseId, position = index, restSeconds = exercise.restSeconds) to
            exercise.sets.mapIndexed { setIndex, set ->
                RoutineSet(
                    routineExerciseId = 0,
                    position = setIndex,
                    type = set.type,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    distanceKm = set.distanceKm,
                    durationSec = set.durationSec,
                )
            }
    }

    /** Shapes the draft like a workout so the editor can reuse the workout screen's exercise cards. */
    fun toBlocks(library: Map<String, Exercise>): List<ExerciseBlock> = exercises.mapIndexed { index, exercise ->
        ExerciseBlock(
            workoutExercise = WorkoutExercise(
                id = exercise.key,
                workoutId = 0,
                exerciseId = exercise.exerciseId,
                position = index,
                restSeconds = exercise.restSeconds,
            ),
            exercise = library[exercise.exerciseId],
            sets = exercise.sets.mapIndexed { setIndex, set ->
                WorkoutSet(
                    id = set.key,
                    workoutExerciseId = exercise.key,
                    position = setIndex,
                    type = set.type,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    distanceKm = set.distanceKm,
                    durationSec = set.durationSec,
                )
            },
        )
    }

    companion object {
        fun from(contents: RoutineContents, nextKey: () -> Long) = RoutineDraft(
            id = contents.routine.id,
            name = contents.routine.name,
            exercises = contents.exercises.map { (exercise, sets) ->
                DraftExercise(
                    key = nextKey(),
                    exerciseId = exercise.exerciseId,
                    restSeconds = exercise.restSeconds,
                    sets = sets.map { DraftSet(nextKey(), it.type, it.weightKg, it.reps, it.distanceKm, it.durationSec) },
                )
            },
        )
    }
}
