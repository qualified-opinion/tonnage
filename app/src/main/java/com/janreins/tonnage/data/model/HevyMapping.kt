package com.janreins.tonnage.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A remembered choice from a Hevy import: this Hevy exercise name is that library exercise. */
@Entity(tableName = "hevy_mappings")
data class HevyMapping(
    /** The Hevy name, lowercased with single spaces (see ExerciseMatcher.normalizeName). */
    @PrimaryKey val hevyName: String,
    val exerciseId: String,
)
