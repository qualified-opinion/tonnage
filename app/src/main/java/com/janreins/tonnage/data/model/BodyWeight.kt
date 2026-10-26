package com.janreins.tonnage.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One body weight entry; at most one per day. Weight is stored in kg. */
@Entity(tableName = "body_weights", indices = [Index(value = ["epochDay"], unique = true)])
data class BodyWeight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The local date, as [LocalDate.toEpochDay]. */
    val epochDay: Long,
    val weightKg: Double,
    val createdAt: Long,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}
