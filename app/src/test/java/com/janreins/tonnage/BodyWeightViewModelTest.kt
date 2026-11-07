package com.janreins.tonnage

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.ui.bodyweight.BodyWeightViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BodyWeightViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun todayMovesOnAtMidnightWhileTheScreenIsOpen() = runTest {
        val base = Instant.parse("2026-11-07T23:59:00Z").toEpochMilli()
        val model = BodyWeightViewModel(app, db, { base + testScheduler.currentTime }, { ZoneId.of("UTC") })
        store.put("body", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.currentDay.collect {} }
        assertEquals(LocalDate.parse("2026-11-07"), model.currentDay.value)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(LocalDate.parse("2026-11-08"), model.currentDay.first { it == LocalDate.parse("2026-11-08") })
        // The new day can now be saved.
        assertTrue(model.save(LocalDate.parse("2026-11-08"), 80.0))
    }

    @Test fun resumeRereadsTheZone() = runTest {
        val now = Instant.parse("2026-11-07T17:00:00Z").toEpochMilli()
        var zone = ZoneId.of("UTC")
        val model = BodyWeightViewModel(app, db, { now }, { zone })
        store.put("body", model)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.currentDay.collect {} }
        assertEquals(LocalDate.parse("2026-11-07"), model.currentDay.value)
        zone = ZoneId.of("Asia/Manila")
        model.refresh()
        assertEquals(LocalDate.parse("2026-11-08"), model.currentDay.first { it == LocalDate.parse("2026-11-08") })
    }
}
