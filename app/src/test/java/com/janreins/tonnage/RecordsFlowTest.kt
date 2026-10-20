package com.janreins.tonnage

import com.janreins.tonnage.data.model.RecordSet
import com.janreins.tonnage.data.repository.recordsFlow
import com.janreins.tonnage.domain.PersonalRecord
import com.janreins.tonnage.domain.PrKind
import com.janreins.tonnage.domain.WorkoutStats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RecordsFlowTest {
    private fun history(weight: Double) = listOf(
        RecordSet(1, 1, "bench", "Bench", "WEIGHT_REPS", 1, 50.0, 5),
        RecordSet(2, 2, "bench", "Bench", "WEIGHT_REPS", 2, weight, 5),
    )

    @Test fun equalRoomSnapshotsDoNotCalculateOrEmitAgain() = runTest {
        val source = MutableSharedFlow<List<RecordSet>>()
        val results = mutableListOf<Map<Long, List<PersonalRecord>>>()
        var calculations = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recordsFlow(source, StandardTestDispatcher(testScheduler)) {
                calculations++
                WorkoutStats.recordsByWorkout(it)
            }.collect { results += it }
        }
        runCurrent()
        source.emit(history(60.0)); runCurrent()
        source.emit(history(60.0).map { it.copy() }); runCurrent()
        assertEquals(1, calculations)
        assertEquals(1, results.size)
    }

    @Test fun aNewEditCancelsAnOlderCalculationAndOnlyTheLatestResultWins() = runTest {
        val source = MutableSharedFlow<List<RecordSet>>()
        val started = CompletableDeferred<Unit>()
        var oldCancelled = false
        val results = mutableListOf<Map<Long, List<PersonalRecord>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recordsFlow(source, StandardTestDispatcher(testScheduler)) {
                if (it.last().weightKg == 60.0) {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { oldCancelled = true }
                } else WorkoutStats.recordsByWorkout(it)
            }.collect { results += it }
        }
        runCurrent()
        source.emit(history(60.0)); runCurrent()
        assertTrue(started.isCompleted)
        source.emit(history(65.0)); runCurrent()
        assertTrue(oldCancelled)
        assertEquals(1, results.size)
        assertEquals(65.0, results.single().getValue(2).single { it.kind == PrKind.HEAVIEST_WEIGHT }.value, 0.0)
    }

    @Test fun calculationRunsAwayFromTheCollectingThreadByDefault() = runBlocking {
        val caller = Thread.currentThread().name
        var worker = ""
        recordsFlow(flowOf(history(60.0)), calculate = {
            worker = Thread.currentThread().name
            WorkoutStats.recordsByWorkout(it)
        }).first()
        assertNotEquals(caller, worker)
        assertTrue(worker.startsWith("DefaultDispatcher"))
    }

    @Test fun twentyThousandSetGuardKeepsTheSameRecords() = runBlocking {
        val rows = (0 until 20_000).map { i ->
            val workout = i / 20 + 1L
            RecordSet(workout, workout * 86_400_000, "exercise-${i % 3}", "Exercise ${i % 3}",
                "WEIGHT_REPS", i + 1L, 40.0 + (i / 20) % 150, 5 + i % 6)
        }
        val expected = WorkoutStats.recordsByWorkout(rows)
        var actual = emptyMap<Long, List<PersonalRecord>>()
        val elapsedMs = measureNanoTime { actual = recordsFlow(flowOf(rows)).first() } / 1_000_000.0
        assertEquals(expected, actual)
        assertTrue(actual.isNotEmpty())
        File("build/reports/records-guard.txt").apply {
            parentFile.mkdirs()
            appendText("sets=${rows.size} records=${actual.values.sumOf { it.size }} elapsedMs=$elapsedMs\n")
        }
    }
}
