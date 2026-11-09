package com.janreins.tonnage.data.repository

import com.janreins.tonnage.data.local.ExerciseDao
import com.janreins.tonnage.data.local.RoutineDao
import com.janreins.tonnage.data.local.WorkoutDao
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineContents
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.RoutineSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.janreins.tonnage.domain.RoutineStats

class RoutineRepository(
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val now: () -> Long = System::currentTimeMillis,
    /** Gives the list when each routine was last done; without it those details are left out. */
    private val workoutDao: WorkoutDao? = null,
) {
    fun observeSummaries(): Flow<List<RoutineSummary>> = combine(
        routineDao.observeRoutines(),
        routineDao.observeAllRoutineExercises(),
        exerciseDao.observeAll(),
        workoutDao?.observeRoutineWorkoutTimes() ?: flowOf(emptyList()),
    ) { routines, routineExercises, exercises, times ->
        val library = exercises.associateBy { it.id }
        val byRoutine = routineExercises.groupBy { it.routineId }
        val stats = RoutineStats.byRoutine(times)
        routines.map { routine ->
            val ids = byRoutine[routine.id].orEmpty().map { it.exerciseId }
            RoutineSummary(
                routine = routine,
                exerciseNames = ids.map { library[it]?.name ?: "Unknown exercise" },
                exercises = ids.mapNotNull { library[it] },
                lastDoneAt = stats[routine.id]?.lastStartedAt,
                typicalDurationMs = stats[routine.id]?.typicalDurationMs,
            )
        }
    }

    suspend fun load(routineId: Long): RoutineContents? {
        val routine = routineDao.getRoutine(routineId) ?: return null
        val sets = routineDao.getSets(routineId).groupBy { it.routineExerciseId }
        return RoutineContents(routine, routineDao.getExercises(routineId).map { it to sets[it.id].orEmpty() })
    }

    /** Saves a new routine (id 0) or overwrites an existing one; returns its id. */
    suspend fun save(
        id: Long,
        name: String,
        exercises: List<Pair<RoutineExercise, List<RoutineSet>>>,
    ): Long = routineDao.save(
        Routine(id = id, name = name.trim().ifEmpty { "Routine" }, createdAt = now()),
        exercises,
    )

    suspend fun delete(routineId: Long) = routineDao.deleteRoutine(routineId)

    /** Copies a routine as "<name> (copy)"; returns the new id, or null if it no longer exists. */
    suspend fun duplicate(routineId: Long): Long? {
        val contents = load(routineId) ?: return null
        return save(0, "${contents.routine.name} (copy)", contents.exercises)
    }
}
