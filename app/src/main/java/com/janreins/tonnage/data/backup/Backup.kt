package com.janreins.tonnage.data.backup

import com.janreins.tonnage.data.model.Exercise
import com.janreins.tonnage.data.model.HevyMapping
import com.janreins.tonnage.data.model.RoutineContents
import com.janreins.tonnage.data.model.SetType
import com.janreins.tonnage.data.model.TrackingType
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineExercise
import com.janreins.tonnage.data.model.RoutineSet
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.data.model.WorkoutExercise
import com.janreins.tonnage.data.model.WorkoutSet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A finished workout with its exercises and sets, as kept in a backup. */
data class BackupWorkout(val workout: Workout, val exercises: List<Pair<WorkoutExercise, List<WorkoutSet>>>)

/** Everything a backup holds. Built-in exercises aren't included; they come with the app. */
data class BackupContents(
    val exportedAt: Long,
    val defaultRestSeconds: Int,
    val customExercises: List<Exercise>,
    val workouts: List<BackupWorkout>,
    val routines: List<RoutineContents>,
    val hevyMappings: List<HevyMapping>,
)

class BackupFormatException(message: String) : Exception(message)

/**
 * The backup file format: JSON with a `schemaVersion`, so a later version of the app can read
 * (and if needed convert) older backups. Bump [SCHEMA_VERSION] when the format changes and keep
 * reading the old versions.
 */
object BackupJson {
    const val FORMAT = "tonnage-backup"
    const val SCHEMA_VERSION = 1

    fun write(contents: BackupContents): String = JSONObject().apply {
        put("format", FORMAT)
        put("schemaVersion", SCHEMA_VERSION)
        put("exportedAt", contents.exportedAt)
        put("settings", JSONObject().put("defaultRestSeconds", contents.defaultRestSeconds))
        put("customExercises", JSONArray(contents.customExercises.map(::exerciseJson)))
        put("workouts", JSONArray(contents.workouts.map(::workoutJson)))
        put("routines", JSONArray(contents.routines.map(::routineJson)))
        put(
            "hevyMappings",
            JSONArray(contents.hevyMappings.map { JSONObject().put("hevyName", it.hevyName).put("exerciseId", it.exerciseId) }),
        )
    }.toString(1)

    fun read(text: String): BackupContents {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("This file isn't a Tonnage backup.")
        }
        if (root.optString("format") != FORMAT) throw BackupFormatException("This file isn't a Tonnage backup.")
        val version = root.optInt("schemaVersion", 0)
        if (version > SCHEMA_VERSION) {
            throw BackupFormatException("This backup was made by a newer version of Tonnage. Update the app, then restore it.")
        }
        if (version < 1) throw BackupFormatException("This backup's version isn't recognised.")
        return try {
            BackupContents(
                exportedAt = root.optLong("exportedAt"),
                defaultRestSeconds = root.optJSONObject("settings")?.optInt("defaultRestSeconds", 90) ?: 90,
                customExercises = root.objects("customExercises").map(::exercise),
                workouts = root.objects("workouts").map(::workout),
                routines = root.objects("routines").map(::routine),
                hevyMappings = root.objects("hevyMappings").map { HevyMapping(it.getString("hevyName"), it.getString("exerciseId")) },
            )
        } catch (e: JSONException) {
            throw BackupFormatException("This backup is damaged and can't be read (${e.message}).")
        }
    }

    // Writing

    private fun exerciseJson(e: Exercise) = JSONObject().apply {
        put("id", e.id)
        put("name", e.name)
        put("category", e.category)
        put("equipment", e.equipment)
        put("primaryMuscles", e.primaryMuscles)
        put("secondaryMuscles", e.secondaryMuscles)
        put("mechanic", e.mechanic)
        put("instructions", e.instructions)
        put("trackingType", e.trackingType.name)
        put("createdAt", e.createdAt)
    }

    private fun workoutJson(w: BackupWorkout) = JSONObject().apply {
        put("title", w.workout.title)
        put("startedAt", w.workout.startedAt)
        putOpt("endedAt", w.workout.endedAt)
        put("notes", w.workout.notes)
        put("source", w.workout.source)
        putOpt("routineId", w.workout.routineId)
        put(
            "exercises",
            JSONArray(
                w.exercises.map { (exercise, sets) ->
                    JSONObject().apply {
                        put("exerciseId", exercise.exerciseId)
                        put("restSeconds", exercise.restSeconds)
                        put("notes", exercise.notes)
                        put(
                            "sets",
                            JSONArray(
                                sets.map { s ->
                                    setJson(s.type, s.weightKg, s.reps, s.distanceKm, s.durationSec).apply {
                                        putOpt("rpe", s.rpe)
                                        put("completed", s.completed)
                                        putOpt("completedAt", s.completedAt)
                                    }
                                },
                            ),
                        )
                    }
                },
            ),
        )
    }

    private fun routineJson(r: RoutineContents) = JSONObject().apply {
        put("id", r.routine.id)
        put("name", r.routine.name)
        put("notes", r.routine.notes)
        put("position", r.routine.position)
        put("createdAt", r.routine.createdAt)
        put(
            "exercises",
            JSONArray(
                r.exercises.map { (exercise, sets) ->
                    JSONObject().apply {
                        put("exerciseId", exercise.exerciseId)
                        put("restSeconds", exercise.restSeconds)
                        put("sets", JSONArray(sets.map { setJson(it.type, it.weightKg, it.reps, it.distanceKm, it.durationSec) }))
                    }
                },
            ),
        )
    }

    private fun setJson(type: SetType, weightKg: Double?, reps: Int?, distanceKm: Double?, durationSec: Int?) = JSONObject().apply {
        put("type", type.name)
        putOpt("weightKg", weightKg)
        putOpt("reps", reps)
        putOpt("distanceKm", distanceKm)
        putOpt("durationSec", durationSec)
    }

    // Reading

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.doubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

    private fun setType(name: String) = SetType.entries.firstOrNull { it.name == name } ?: SetType.NORMAL

    private fun exercise(o: JSONObject) = Exercise(
        id = o.getString("id"),
        name = o.getString("name"),
        category = o.optString("category", "custom"),
        equipment = o.optString("equipment", "other"),
        primaryMuscles = o.optString("primaryMuscles"),
        secondaryMuscles = o.optString("secondaryMuscles"),
        mechanic = o.optString("mechanic"),
        instructions = o.optString("instructions"),
        trackingType = TrackingType.entries.firstOrNull { it.name == o.optString("trackingType") } ?: TrackingType.WEIGHT_REPS,
        isCustom = true,
        createdAt = o.optLong("createdAt"),
    )

    private fun workout(o: JSONObject) = BackupWorkout(
        workout = Workout(
            title = o.getString("title"),
            startedAt = o.getLong("startedAt"),
            endedAt = o.longOrNull("endedAt") ?: o.getLong("startedAt"),
            notes = o.optString("notes"),
            source = o.optString("source", Workout.SOURCE_APP),
            routineId = o.longOrNull("routineId"),
        ),
        exercises = o.objects("exercises").mapIndexed { index, e ->
            WorkoutExercise(
                workoutId = 0,
                exerciseId = e.getString("exerciseId"),
                position = index,
                restSeconds = e.optInt("restSeconds", 90),
                notes = e.optString("notes"),
            ) to e.objects("sets").mapIndexed { setIndex, s ->
                WorkoutSet(
                    workoutExerciseId = 0,
                    position = setIndex,
                    type = setType(s.optString("type")),
                    weightKg = s.doubleOrNull("weightKg"),
                    reps = s.intOrNull("reps"),
                    distanceKm = s.doubleOrNull("distanceKm"),
                    durationSec = s.intOrNull("durationSec"),
                    rpe = s.doubleOrNull("rpe"),
                    completed = s.optBoolean("completed", true),
                    completedAt = s.longOrNull("completedAt"),
                )
            }
        },
    )

    private fun routine(o: JSONObject) = RoutineContents(
        routine = Routine(
            id = o.getLong("id"),
            name = o.getString("name"),
            notes = o.optString("notes"),
            position = o.optInt("position"),
            createdAt = o.optLong("createdAt"),
        ),
        exercises = o.objects("exercises").mapIndexed { index, e ->
            RoutineExercise(routineId = 0, exerciseId = e.getString("exerciseId"), position = index, restSeconds = e.optInt("restSeconds", 90)) to
                e.objects("sets").mapIndexed { setIndex, s ->
                    RoutineSet(
                        routineExerciseId = 0,
                        position = setIndex,
                        type = setType(s.optString("type")),
                        weightKg = s.doubleOrNull("weightKg"),
                        reps = s.intOrNull("reps"),
                        distanceKm = s.doubleOrNull("distanceKm"),
                        durationSec = s.intOrNull("durationSec"),
                    )
                }
        },
    )
}
