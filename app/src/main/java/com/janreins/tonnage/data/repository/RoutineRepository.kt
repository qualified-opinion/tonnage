package com.janreins.tonnage.data.repository

import com.janreins.tonnage.data.local.ExerciseDao
import com.janreins.tonnage.data.local.RoutineDao
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineContents
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.RoutineSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoutineRepository(
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun observeSummaries(): Flow<List<RoutineSummary>> = combine(
        routineDao.observeRoutines(),
        routineDao.observeAllRoutineExercises(),
        exerciseDao.observeAll(),
    ) { routines, routineExercises, exercises ->
        val names = exercises.associate { it.id to it.name }
        val byRoutine = routineExercises.groupBy { it.routineId }
        routines.map { routine ->
            RoutineSummary(routine, byRoutine[routine.id].orEmpty().map { names[it.exerciseId] ?: "Unknown exercise" })
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
