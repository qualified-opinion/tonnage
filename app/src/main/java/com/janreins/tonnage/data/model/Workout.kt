package com.janreins.tonnage.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One training session. A workout with [endedAt] == null is the active (in-progress) workout;
 * it is written to the database as you log, so it survives the app being killed.
 */
@Entity(tableName = "workouts", indices = [Index("endedAt")])
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val notes: String = "",
    val source: String = SOURCE_APP,
    /** The routine it was started from, if any. Not a foreign key: deleting a routine keeps history. */
    val routineId: Long? = null,
) {
    val isActive: Boolean get() = endedAt == null

    companion object {
        const val SOURCE_APP = "app"
        const val SOURCE_HEVY = "hevy"
    }
}

/** An exercise inside a workout, in the order it was added. */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val position: Int,
    /** Rest timer started after completing a set of this exercise; 0 turns it off. */
    val restSeconds: Int,
    val notes: String = "",
)

enum class SetType(val shortLabel: String, val label: String) {
    WARMUP("W", "Warm-up"),
    NORMAL("", "Normal"),
    DROP("D", "Drop set"),
    FAILURE("F", "Failure"),
}

/** One set. Weights are always stored in kg; other units are display-only. */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val position: Int,
    val type: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distanceKm: Double? = null,
    val durationSec: Int? = null,
    val rpe: Double? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

/** A finished workout with totals, for the history list. */
data class WorkoutSummary(
    val id: Long,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double,
)

/** A completed working set of a finished workout, in date order, for working out records over all history. */
data class RecordSet(
    val workoutId: Long,
    val startedAt: Long,
    val exerciseId: String,
    val exerciseName: String?,
    /** Stored name of the exercise's [TrackingType]; null if the exercise is missing. */
    val trackingType: String?,
    val setId: Long,
    val weightKg: Double?,
    val reps: Int?,
)

/** A completed working set from an earlier finished workout, used for personal records. */
data class PastSet(
    val exerciseId: String,
    val weightKg: Double?,
    val reps: Int?,
)
