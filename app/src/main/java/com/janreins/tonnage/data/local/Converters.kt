package com.janreins.tonnage.data.local

import androidx.room.TypeConverter
import com.janreins.tonnage.data.model.TrackingType

class Converters {
    @TypeConverter
    fun fromTrackingType(value: TrackingType): String = value.name

    @TypeConverter
    fun toTrackingType(value: String): TrackingType =
        TrackingType.entries.firstOrNull { it.name == value } ?: TrackingType.WEIGHT_REPS
}
