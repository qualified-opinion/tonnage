package com.janreins.tonnage.data.local

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.TrackingType
import org.json.JSONArray
import org.json.JSONObject

/** Parses the bundled `assets/exercises.json` (free-exercise-db format) into library rows. */
object ExerciseSeed {
    const val ASSET_NAME = "exercises.json"

    fun parse(json: String): List<Exercise> {
        val array = JSONArray(json)
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
    }

    private fun fromJson(obj: JSONObject): Exercise {
        val category = obj.optStringOrEmpty("category")
        val equipment = obj.optStringOrEmpty("equipment").ifEmpty { "none" }
        return Exercise(
            id = obj.getString("id"),
            name = obj.getString("name"),
            category = category,
            equipment = equipment,
            primaryMuscles = Exercise.joinList(obj.stringList("primaryMuscles")),
            secondaryMuscles = Exercise.joinList(obj.stringList("secondaryMuscles")),
            mechanic = obj.optStringOrEmpty("mechanic"),
            instructions = obj.stringList("instructions").joinToString("\n"),
            trackingType = trackingTypeFor(category, equipment),
        )
    }

    fun trackingTypeFor(category: String, equipment: String): TrackingType = when {
        category == "cardio" -> TrackingType.DISTANCE_DURATION
        category == "stretching" -> TrackingType.DURATION
        equipment == "body only" || equipment == "none" -> TrackingType.BODYWEIGHT_REPS
        else -> TrackingType.WEIGHT_REPS
    }

    private fun JSONObject.optStringOrEmpty(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }
}
