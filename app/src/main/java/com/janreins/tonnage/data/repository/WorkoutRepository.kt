package com.janreins.tonnage.data.repository

import com.janreins.tonnage.data.local.ExerciseDao
import com.janreins.tonnage.data.local.WorkoutDao
import com.janreins.tonnage.data.model.RoutineContents
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import com.janreins.tonnage.data.model.WorkoutSummary
import com.janreins.tonnage.domain.ExerciseBlock
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.domain.WorkoutStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/** The active workout with everything the logging screen shows. */
data class ActiveWorkout(val workout: Workout, val blocks: List<ExerciseBlock>)

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // Edits arrive one keystroke at a time; a fair lock keeps them in the order they were typed.
    private val writeLock = Mutex()

    private suspend fun <T> write(block: suspend () -> T): T = writeLock.withLock { block() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActive(): Flow<ActiveWorkout?> = workoutDao.observeActive().flatMapLatest { workout ->
        if (workout == null) {
            flowOf(null)
        } else {
            combine(
                workoutDao.observeWorkoutExercises(workout.id),
                workoutDao.observeSetsForWorkout(workout.id),
                exerciseDao.observeAll().map { all -> all.associateBy { it.id } },
            ) { workoutExercises, sets, exercises ->
                val setsByExercise = sets.groupBy { it.workoutExerciseId }
                ActiveWorkout(
                    workout = workout,
                    blocks = workoutExercises.map { we ->
                        ExerciseBlock(we, exercises[we.exerciseId], setsByExercise[we.id].orEmpty())
                    },
                )
            }
        }
    }

    fun observeHistory(): Flow<List<WorkoutSummary>> = workoutDao.observeFinishedSummaries()

    suspend fun getActive(): Workout? = workoutDao.getActive()

    /** Starts an empty workout, or returns the one already in progress. */
    suspend fun startEmpty(): Long = write {
        workoutDao.getActive()?.id ?: workoutDao.insertWorkout(
            Workout(title = defaultTitle(now()), startedAt = now()),
        )
    }

    /**
     * Starts a workout from a routine, with its exercises and planned sets filled in,
     * or returns the workout already in progress.
     */
    suspend fun startFromRoutine(contents: RoutineContents): Long = write {
        workoutDao.getActive()?.id ?: workoutDao.insertWorkoutWithContents(
            Workout(title = contents.routine.name, startedAt = now(), routineId = contents.routine.id),
            contents.exercises.map { (exercise, sets) ->
                val workoutExercise = WorkoutExercise(
                    workoutId = 0,
                    exerciseId = exercise.exerciseId,
                    position = exercise.position,
                    restSeconds = exercise.restSeconds,
                )
                val workoutSets = sets.map {
                    WorkoutSet(
                        workoutExerciseId = 0,
                        position = it.position,
                        type = it.type,
                        weightKg = it.weightKg,
                        reps = it.reps,
                        distanceKm = it.distanceKm,
                        durationSec = it.durationSec,
                    )
                }.ifEmpty { listOf(WorkoutSet(workoutExerciseId = 0, position = 0)) }
                workoutExercise to workoutSets
            },
        )
    }

    /**
     * What was done last time for each exercise: the completed sets from the latest finished
     * workout of the same routine if there is one, otherwise from the latest workout anywhere.
     */
    suspend fun previousSets(exerciseIds: Collection<String>, routineId: Long?): Map<String, List<WorkoutSet>> =
        exerciseIds.associateWith { exerciseId ->
            val workoutId = routineId?.let { workoutDao.latestWorkoutWith(exerciseId, it) }
                ?: workoutDao.latestWorkoutWith(exerciseId, null)
            workoutId?.let { workoutDao.completedSetsOf(it, exerciseId) }.orEmpty()
        }

    suspend fun rename(workoutId: Long, title: String) = write {
        workoutDao.updateTitle(workoutId, title.trim().ifEmpty { "Workout" })
    }

    /** Adds exercises at the end, each with one empty set. */
    suspend fun addExercises(workoutId: Long, exerciseIds: List<String>, restSeconds: Int) = write {
        var position = workoutDao.maxExercisePosition(workoutId)
        exerciseIds.forEach { exerciseId ->
            position += 1
            val id = workoutDao.insertWorkoutExercise(
                WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = position, restSeconds = restSeconds),
            )
            workoutDao.insertSet(WorkoutSet(workoutExerciseId = id, position = 0))
        }
    }

    suspend fun removeExercise(workoutExerciseId: Long) = write { workoutDao.deleteWorkoutExercise(workoutExerciseId) }

    suspend fun moveExercise(workoutId: Long, workoutExerciseId: Long, direction: Int) = write {
        workoutDao.moveExercise(workoutId, workoutExerciseId, direction)
    }

    suspend fun setRest(workoutExerciseId: Long, seconds: Int) = write {
        workoutDao.updateRestSeconds(workoutExerciseId, seconds)
    }

    /** Adds a set after the last one, pre-filled with its values. */
    suspend fun addSet(workoutExerciseId: Long) = write {
        val last = workoutDao.getSets(workoutExerciseId).lastOrNull()
        workoutDao.insertSet(
            WorkoutSet(
                workoutExerciseId = workoutExerciseId,
                position = (last?.position ?: -1) + 1,
                weightKg = last?.weightKg,
                reps = last?.reps,
                distanceKm = last?.distanceKm,
                durationSec = last?.durationSec,
            ),
        )
    }

    suspend fun deleteSet(setId: Long) = write { workoutDao.deleteSet(setId) }
    suspend fun setType(setId: Long, type: SetType) = write { workoutDao.updateType(setId, type) }
    suspend fun setWeight(setId: Long, kg: Double?) = write { workoutDao.updateWeight(setId, kg) }
    suspend fun setReps(setId: Long, reps: Int?) = write { workoutDao.updateReps(setId, reps) }
    suspend fun setDistance(setId: Long, km: Double?) = write { workoutDao.updateDistance(setId, km) }
    suspend fun setDuration(setId: Long, seconds: Int?) = write { workoutDao.updateDuration(setId, seconds) }

    /** Fills a set with the numbers from another (tapping a "previous" value). */
    suspend fun copyValues(setId: Long, from: WorkoutSet) = write {
        workoutDao.updateValues(setId, from.weightKg, from.reps, from.distanceKm, from.durationSec)
    }

    suspend fun setCompleted(setId: Long, completed: Boolean) = write {
        workoutDao.updateCompleted(setId, completed, if (completed) now() else null)
    }

    suspend fun recordsFor(active: ActiveWorkout): List<PersonalRecord> {
        val ids = active.blocks.map { it.workoutExercise.exerciseId }.distinct()
        if (ids.isEmpty()) return emptyList()
        val history = workoutDao.pastWorkingSets(ids, active.workout.id)
        return WorkoutStats.detectRecords(active.blocks, history)
    }

    suspend fun finish(workoutId: Long) = write { workoutDao.finishWorkout(workoutId, now()) }

    suspend fun discard(workoutId: Long) = write { workoutDao.deleteWorkout(workoutId) }

    companion object {
        fun defaultTitle(timeMs: Long): String {
            val hour = Calendar.getInstance().apply { timeInMillis = timeMs }.get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..11 -> "Morning workout"
                in 12..16 -> "Afternoon workout"
                in 17..21 -> "Evening workout"
                else -> "Night workout"
            }
        }
    }
}
