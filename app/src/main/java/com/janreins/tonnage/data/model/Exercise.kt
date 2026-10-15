package com.janreins.tonnage.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** How sets of an exercise are logged. */
enum class TrackingType(val label: String) {
    WEIGHT_REPS("Weight & reps"),
    BODYWEIGHT_REPS("Bodyweight reps"),
    DURATION("Duration"),
    DISTANCE_DURATION("Distance & duration"),
}

/**
 * An exercise in the library. Built-in exercises come from the bundled free-exercise-db list
 * (public domain); custom ones are created by the user and have [isCustom] set.
 *
 * Muscle lists are stored comma-separated and instructions newline-separated to keep the table flat.
 */
@Entity(tableName = "exercises", indices = [Index("name")])
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val equipment: String,
    val primaryMuscles: String,
    val secondaryMuscles: String = "",
    val mechanic: String = "",
    val instructions: String = "",
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val isCustom: Boolean = false,
    val createdAt: Long = 0L,
) {
    val primaryMuscleList: List<String> get() = splitList(primaryMuscles)
    val secondaryMuscleList: List<String> get() = splitList(secondaryMuscles)
    val instructionSteps: List<String> get() = instructions.split('\n').filter { it.isNotBlank() }

    companion object {
        fun joinList(values: List<String>): String = values.joinToString(",") { it.trim() }
        fun splitList(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
