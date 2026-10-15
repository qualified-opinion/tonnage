package com.janreins.tonnage.data.local

import androidx.room.TypeConverter
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType

class Converters {
    @TypeConverter
    fun fromTrackingType(value: TrackingType): String = value.name

    @TypeConverter
    fun toTrackingType(value: String): TrackingType =
        TrackingType.entries.firstOrNull { it.name == value } ?: TrackingType.WEIGHT_REPS

    @TypeConverter
    fun fromSetType(value: SetType): String = value.name

    @TypeConverter
    fun toSetType(value: String): SetType =
        SetType.entries.firstOrNull { it.name == value } ?: SetType.NORMAL
}
