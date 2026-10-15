package com.janreins.tonnage

import com.janreins.tonnage.data.hevy.HevyCsv
import com.janreins.tonnage.data.hevy.HevyFormatException
import com.janreins.tonnage.data.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class HevyCsvTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private fun sample() = javaClass.getResource("/hevy_sample.csv")!!.readText()

    @Test
    fun groupsRowsIntoWorkoutsOldestFirst() {
        val workouts = HevyCsv.parse(sample(), utc)

        assertEquals(listOf("Morning workout ☀️", "Afternoon workout 💪", "Push, heavy"), workouts.map { it.title })
        val push = workouts.last()
        assertEquals(HevyCsv.parseDate("22 Aug 2026, 09:03", utc), push.startedAt)
        assertEquals(103 * 60_000L, push.endedAt - push.startedAt)
        assertEquals("Felt strong", push.description)
        assertEquals(listOf("Bench Press (Barbell)", "Kneeling Push Up", "Face Pull"), push.exercises.map { it.name })
        assertEquals("Pause reps", push.exercises[0].notes)
    }

    @Test
    fun readsSetTypesNumbersAndBlanks() {
        val push = HevyCsv.parse(sample(), utc).last()
        val bench = push.exercises[0].sets
        assertEquals(listOf(SetType.WARMUP, SetType.NORMAL, SetType.FAILURE), bench.map { it.type })
        assertEquals(60.0, bench[1].weightKg!!, 0.0)
        assertEquals(8, bench[1].reps)
        assertEquals(8.5, bench[1].rpe!!, 0.0)
        assertEquals(SetType.DROP, push.exercises[2].sets.single().type)

        val pushUp = push.exercises[1].sets.single()
        assertNull(pushUp.weightKg)
        assertEquals(15, pushUp.reps)

        val walk = HevyCsv.parse(sample(), utc)[1].exercises.single().sets.single()
        assertEquals(7.0, walk.distanceKm!!, 0.0)
        assertEquals(3000, walk.durationSec)
        assertNull(walk.reps)
    }

    @Test
    fun poundsAndMilesAreConvertedToMetric() {
        val csv = """
            title,start_time,end_time,exercise_title,set_type,weight_lbs,reps,distance_miles,duration_seconds
            Legs,2026-08-01 10:00:00,2026-08-01 11:00:00,Squat (Barbell),normal,135,5,,
            Legs,2026-08-01 10:00:00,2026-08-01 11:00:00,Running,normal,,,1,600
        """.trimIndent()
        val workout = HevyCsv.parse(csv, utc).single()
        assertEquals(61.23, workout.exercises[0].sets.single().weightKg!!, 0.0)
        assertEquals(1.609, workout.exercises[1].sets.single().distanceKm!!, 0.001)
    }

    @Test
    fun quotedFieldsKeepCommasQuotesAndLineBreaks() {
        val rows = HevyCsv.parseRows("a,\"b, c\",\"say \"\"hi\"\"\"\r\n\"line\nbreak\",2")
        assertEquals(listOf(listOf("a", "b, c", "say \"hi\""), listOf("line\nbreak", "2")), rows)
    }

    @Test(expected = HevyFormatException::class)
    fun otherCsvFilesAreRejected() {
        HevyCsv.parse("date,amount\n2026-01-01,12.50")
    }
}
