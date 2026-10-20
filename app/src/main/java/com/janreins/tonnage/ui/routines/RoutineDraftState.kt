package com.janreins.tonnage.ui.routines

import com.janreins.tonnage.data.model.SetType
import org.json.JSONArray
import org.json.JSONObject

/** A bounded saved-state string; both versions preserve the editor's discard-changes check. */
internal object RoutineDraftState {
    const val KEY = "routine_editor"
    // At most about 64 KiB as a parcelled UTF-16 string, leaving room for other screen state.
    const val MAX_CHARS = 32_000

    fun encode(editor: RoutineEditor): String? = runCatching {
        JSONObject().put("v", 1).put("original", encodeDraft(editor.original)).put("draft", encodeDraft(editor.draft))
            .toString().takeIf { it.length <= MAX_CHARS }
    }.getOrNull()

    fun decode(text: String?): RoutineEditor? = runCatching {
        require(text != null && text.length <= MAX_CHARS)
        val json = JSONObject(text)
        require(json.getInt("v") == 1)
        RoutineEditor(decodeDraft(json.getJSONObject("original")), decodeDraft(json.getJSONObject("draft")))
    }.getOrNull()

    private fun encodeDraft(draft: RoutineDraft): JSONObject = JSONObject()
        .put("id", draft.id).put("name", draft.name).put("exercises", JSONArray(draft.exercises.map { exercise ->
            JSONObject().put("key", exercise.key).put("id", exercise.exerciseId).put("rest", exercise.restSeconds)
                .put("sets", JSONArray(exercise.sets.map { set ->
                    JSONObject().put("key", set.key).put("type", set.type.name).put("weight", set.weightKg)
                        .put("reps", set.reps).put("distance", set.distanceKm).put("duration", set.durationSec)
                }))
        }))

    private fun decodeDraft(json: JSONObject): RoutineDraft {
        val exercises = json.getJSONArray("exercises")
        return RoutineDraft(json.getLong("id"), json.getString("name"), (0 until exercises.length()).map { index ->
            val exercise = exercises.getJSONObject(index)
            val sets = exercise.getJSONArray("sets")
            DraftExercise(exercise.getLong("key"), exercise.getString("id"), exercise.getInt("rest"), (0 until sets.length()).map { i ->
                val set = sets.getJSONObject(i)
                DraftSet(set.getLong("key"), SetType.valueOf(set.getString("type")),
                    if (set.has("weight")) set.getDouble("weight") else null,
                    if (set.has("reps")) set.getInt("reps") else null,
                    if (set.has("distance")) set.getDouble("distance") else null,
                    if (set.has("duration")) set.getInt("duration") else null)
            })
        })
    }
}
