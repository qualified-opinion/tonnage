package com.janreins.tonnage.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved workout template, e.g. "Push A". */
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val position: Int = 0,
    val createdAt: Long = 0L,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: String,
    val position: Int,
    val restSeconds: Int,
)

/** A planned set; its numbers are the starting values when the routine is started. */
@Entity(
    tableName = "routine_sets",
    foreignKeys = [
        ForeignKey(
            entity = RoutineExercise::class,
            parentColumns = ["id"],
            childColumns = ["routineExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineExerciseId")],
)
data class RoutineSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineExerciseId: Long,
    val position: Int,
    val type: SetType = SetType.NORMAL,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distanceKm: Double? = null,
    val durationSec: Int? = null,
)

/** Everything in one routine, exercises in order with their sets. */
data class RoutineContents(val routine: Routine, val exercises: List<Pair<RoutineExercise, List<RoutineSet>>>)

/** A routine with the names of its exercises, for the routine list. */
data class RoutineSummary(val routine: Routine, val exerciseNames: List<String>)
