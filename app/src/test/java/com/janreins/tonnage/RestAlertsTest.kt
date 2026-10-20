package com.janreins.tonnage

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Workout
import com.janreins.tonnage.service.WorkoutNotifications
import com.janreins.tonnage.timer.RestTimer
import com.janreins.tonnage.timer.WorkoutSession
import com.janreins.tonnage.ui.theme.TonnageTheme
import com.janreins.tonnage.ui.workout.RestNotificationNotice
import com.janreins.tonnage.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestAlertsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private val store = ViewModelStore()
    private var starts = 0
    private var running = false
    private var vibrations = 0
    private val originalClock = RestTimer.clock

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.allowMainThreadQueries().build()
        WorkoutSession.confirmedInThisProcess = true
        RestTimer.skip()
    }

    @After fun tearDown() {
        store.clear()
        dispatcher.scheduler.runCurrent()
        db.close()
        RestTimer.skip()
        RestTimer.clock = originalClock
        WorkoutSession.confirmedInThisProcess = false
        WorkoutSession.info.value = null
        Dispatchers.resetMain()
    }

    private fun model() = WorkoutViewModel(app, db, { running }, { starts++ }, { vibrations++ })
        .also { store.put("workout", it) }

    @Test fun initStartsMissingServiceAndResumeRetriesButRunningOrFinishedWorkoutsDoNot() = runTest {
        val id = db.workoutDao().insertWorkout(Workout(title = "Bench", startedAt = 1))
        val model = model()
        dispatcher.scheduler.runCurrent()
        assertEquals(1, starts) // Fake starter leaves it stopped, like a refused foreground start.
        model.ensureService(); dispatcher.scheduler.runCurrent()
        assertEquals(2, starts)
        running = true
        model.ensureService(); dispatcher.scheduler.runCurrent()
        assertEquals(2, starts)
        running = false
        db.workoutDao().deleteWorkout(id)
        model.ensureService(); dispatcher.scheduler.runCurrent()
        assertEquals(2, starts)
    }

    @Test fun pendingWorkoutWaitsForTheUserToAcceptResume() = runTest {
        WorkoutSession.confirmedInThisProcess = false
        db.workoutDao().insertWorkout(Workout(title = "Bench", startedAt = 1))
        val model = model()
        dispatcher.scheduler.runCurrent()
        model.ensureService(); dispatcher.scheduler.runCurrent()
        assertNotNull(model.pendingResume.value)
        assertEquals(0, starts)
        model.resumePending()
        assertEquals(1, starts)
    }

    @Test fun permissionIsRequestedOncePerWorkoutAndCanBeRequestedAfterProcessRecovery() {
        val current = model()
        assertTrue(current.claimNotificationPermission(1))
        assertFalse(current.claimNotificationPermission(1))
        assertTrue(current.claimNotificationPermission(2))
        assertFalse(current.claimNotificationPermission(2))
        assertTrue(model().claimNotificationPermission(2))
    }

    @Test fun fallbackVibratesOnlyOnceForAnExpiredUnclaimedRest() {
        val model = model()
        var now = 1_000L
        RestTimer.clock = { now }
        RestTimer.start(1, "Bench", null)
        val rest = RestTimer.state.value!!
        model.finishRest(rest)
        assertEquals(0, vibrations)
        now += 1000
        model.finishRest(rest)
        model.finishRest(rest)
        assertEquals(1, vibrations)
        assertFalse(RestTimer.finish(rest)) // The service cannot claim it again.
        RestTimer.start(1, "Squat", null)
        model.finishRest(rest) // A stale screen cannot end a newer rest.
        assertNotNull(RestTimer.state.value)
        assertEquals(1, vibrations)
    }

    private fun noticeVisible(expected: Boolean) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var settingsOpened = false
        try {
            compose.runOnUiThread {
                activity.get().setContent {
                    TonnageTheme(darkTheme = false) {
                        RestNotificationNotice(WorkoutNotifications.restAlertsEnabled(app)) { settingsOpened = true }
                    }
                }
            }
            val notice = compose.onNodeWithText("Rest notifications are off")
            if (expected) {
                notice.assertExists()
                compose.onNodeWithText("Notification settings").performClick()
                assertTrue(settingsOpened)
            } else notice.assertDoesNotExist()
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun noticeAppearsWhenAppNotificationsAreBlocked() {
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        noticeVisible(true)
    }

    @Test fun noticeAppearsWhenOnlyTheRestChannelIsBlocked() {
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
        manager.createNotificationChannel(NotificationChannel(WorkoutNotifications.CHANNEL_REST_DONE, "Rest finished", NotificationManager.IMPORTANCE_NONE))
        noticeVisible(true)
    }

    @Test fun noticeIsHiddenWhenRestAlertsAreEnabled() {
        WorkoutNotifications.createChannels(app)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        noticeVisible(false)
    }
}
