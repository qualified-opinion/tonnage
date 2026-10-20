package com.janreins.tonnage.timer

import android.content.SharedPreferences

/** Small synchronous checkpoints: a killed process must not revive an already skipped rest. */
internal class RestTimerStore(
    private val prefs: SharedPreferences,
    private val elapsed: () -> Long = { RestTimer.clock() },
    private val wall: () -> Long = System::currentTimeMillis,
) {
    fun save(state: RestTimer.State?) {
        val edit = prefs.edit().clear()
        if (state != null) {
            val now = elapsed()
            edit.putLong("end", state.endsAt).putLong("total", state.totalMs)
                .putLong("savedAt", now).putLong("wallEnd", wall() + state.endsAt - now)
                .putString("exercise", state.exerciseName).putString("next", state.nextSet)
        }
        edit.commit()
    }

    fun restore(): RestTimer.State? {
        if (!prefs.contains("end")) return null
        val now = elapsed()
        val end = prefs.getLong("end", 0)
        val total = prefs.getLong("total", 0)
        val remaining = end - now
        val wallRemaining = prefs.getLong("wallEnd", 0) - wall()
        if (now < prefs.getLong("savedAt", Long.MAX_VALUE) || total <= 0 || remaining !in 1..total ||
            wallRemaining !in (remaining - 3_000)..(remaining + 3_000)
        ) {
            save(null)
            return null
        }
        return RestTimer.State(end, total, prefs.getString("exercise", "").orEmpty(), prefs.getString("next", null))
    }
}
