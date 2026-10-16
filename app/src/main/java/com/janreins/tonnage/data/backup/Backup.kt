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
import org.json.JSONTokener
import java.math.BigDecimal

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

class BackupFormatException(message: String = "This file is incomplete or damaged. Nothing was changed.") : Exception(message)

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
            JSONObject(ExactNumberTokener(text))
        } catch (e: JSONException) {
            throw BackupFormatException()
        }
        if (root.opt("format") != FORMAT) throw BackupFormatException()
        val version = root.intValue("schemaVersion")
        if (version > SCHEMA_VERSION) {
            throw BackupFormatException("This backup was made by a newer version of Tonnage. Update the app, then restore it.")
        }
        if (version < 1) throw BackupFormatException("This backup's version isn't recognised.")
        return try {
            BackupContents(
                exportedAt = root.longValue("exportedAt"),
                defaultRestSeconds = root.getJSONObject("settings").intValue("defaultRestSeconds"),
                customExercises = root.objects("customExercises").map(::exercise),
                workouts = root.objects("workouts").map(::workout),
                routines = root.objects("routines").map(::routine),
                hevyMappings = root.objects("hevyMappings").map { HevyMapping(it.string("hevyName"), it.string("exerciseId")) },
            )
        } catch (e: JSONException) {
            throw BackupFormatException()
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

    /** Keep integer tokens exact: JSONTokener otherwise rounds fractions/overflow through Double. */
    private class ExactNumberTokener(text: String) : JSONTokener(text) {
        override fun nextValue(): Any {
            val first = nextClean()
            back()
            if (first == '-' || first in '0'..'9') {
                return nextTo(",]} \t\r\n").toBigDecimalOrNull() ?: throw syntaxError("Invalid number")
            }
            return super.nextValue()
        }
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = getJSONArray(key)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.string(key: String): String = get(key) as? String ?: throw BackupFormatException()
    private fun JSONObject.boolean(key: String): Boolean = get(key) as? Boolean ?: throw BackupFormatException()

    private fun JSONObject.number(key: String): BigDecimal {
        val value = opt(key) as? Number ?: throw BackupFormatException()
        val decimal = value.toString().toBigDecimalOrNull() ?: throw BackupFormatException()
        if (decimal.signum() < 0) throw BackupFormatException()
        return decimal
    }

    private fun JSONObject.longValue(key: String): Long = try {
        number(key).longValueExact()
    } catch (e: ArithmeticException) {
        throw BackupFormatException()
    }

    private fun JSONObject.intValue(key: String): Int {
        val value = longValue(key)
        if (value > Int.MAX_VALUE) throw BackupFormatException()
        return value.toInt()
    }

    private fun routineId(value: Long): Long {
        if (value !in 1L..Int.MAX_VALUE.toLong()) throw BackupFormatException()
        return value
    }

    // putOpt omits nullable numbers in the exporter; their absence is intentional.
    private fun JSONObject.doubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = number(key).toDouble()
        if (!value.isFinite()) throw BackupFormatException()
        return value
    }

    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) intValue(key) else null
    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) longValue(key) else null

    private fun setType(name: String) = SetType.entries.firstOrNull { it.name == name } ?: SetType.NORMAL

    private fun exercise(o: JSONObject) = Exercise(
        id = o.string("id"),
        name = o.string("name"),
        category = o.string("category"),
        equipment = o.string("equipment"),
        primaryMuscles = o.string("primaryMuscles"),
        secondaryMuscles = o.string("secondaryMuscles"),
        mechanic = o.string("mechanic"),
        instructions = o.string("instructions"),
        trackingType = o.string("trackingType").let { name -> TrackingType.entries.firstOrNull { it.name == name } } ?: TrackingType.WEIGHT_REPS,
        isCustom = true,
        createdAt = o.longValue("createdAt"),
    )

    private fun workout(o: JSONObject) = BackupWorkout(
        workout = Workout(
            title = o.string("title"),
            startedAt = o.longValue("startedAt"),
            endedAt = o.longOrNull("endedAt") ?: o.longValue("startedAt"),
            notes = o.string("notes"),
            source = o.string("source"),
            routineId = o.longOrNull("routineId")?.let(::routineId),
        ),
        exercises = o.objects("exercises").mapIndexed { index, e ->
            WorkoutExercise(
                workoutId = 0,
                exerciseId = e.string("exerciseId"),
                position = index,
                restSeconds = e.intValue("restSeconds"),
                notes = e.string("notes"),
            ) to e.objects("sets").mapIndexed { setIndex, s ->
                WorkoutSet(
                    workoutExerciseId = 0,
                    position = setIndex,
                    type = setType(s.string("type")),
                    weightKg = s.doubleOrNull("weightKg"),
                    reps = s.intOrNull("reps"),
                    distanceKm = s.doubleOrNull("distanceKm"),
                    durationSec = s.intOrNull("durationSec"),
                    rpe = s.doubleOrNull("rpe"),
                    completed = s.boolean("completed"),
                    completedAt = s.longOrNull("completedAt"),
                )
            }
        },
    )

    private fun routine(o: JSONObject) = RoutineContents(
        routine = Routine(
            id = routineId(o.longValue("id")),
            name = o.string("name"),
            notes = o.string("notes"),
            position = o.intValue("position"),
            createdAt = o.longValue("createdAt"),
        ),
        exercises = o.objects("exercises").mapIndexed { index, e ->
            RoutineExercise(routineId = 0, exerciseId = e.string("exerciseId"), position = index, restSeconds = e.intValue("restSeconds")) to
                e.objects("sets").mapIndexed { setIndex, s ->
                    RoutineSet(
                        routineExerciseId = 0,
                        position = setIndex,
                        type = setType(s.string("type")),
                        weightKg = s.doubleOrNull("weightKg"),
                        reps = s.intOrNull("reps"),
                        distanceKm = s.doubleOrNull("distanceKm"),
                        durationSec = s.intOrNull("durationSec"),
                    )
                }
        },
    )
}
