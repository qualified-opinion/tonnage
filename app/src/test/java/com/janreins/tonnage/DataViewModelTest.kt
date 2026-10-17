package com.janreins.tonnage

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.ui.data.BackupState
import com.janreins.tonnage.ui.data.DataViewModel
import com.janreins.tonnage.ui.data.ImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DataViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var model: DataViewModel
    private lateinit var store: ViewModelStore
    private val streams = mutableListOf<SlowStream>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
        model = DataViewModel(application)
        store = ViewModelStore().also { it.put("data", model) }
    }

    @After
    fun tearDown() {
        streams.forEach { it.release.countDown() }
        store.clear()
        dispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    private fun csv(title: String) = "title,start_time,exercise_title\n$title,2026-08-01 10:00,Plank"

    private fun startSlow(fail: Boolean = false): Pair<SlowStream, Job> {
        val stream = SlowStream(csv("Old").toByteArray(), fail).also { streams += it }
        val uri = Uri.parse("content://test/old")
        shadowOf(application.contentResolver).registerInputStream(uri, stream)
        val previousJobs = model.viewModelScope.coroutineContext[Job]!!.children.toSet()
        model.startImport(uri)
        dispatcher.scheduler.runCurrent()
        assertTrue("Reader did not start", stream.entered.await(5, TimeUnit.SECONDS))
        return stream to model.viewModelScope.coroutineContext[Job]!!.children.single { it !in previousJobs }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            dispatcher.scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(5)
        }
        assertTrue("Timed out; state = ${model.importState.value}", condition())
    }

    @Test
    fun closingCancelsTheReaderAndItCannotReopenTheImport() {
        val (old, job) = startSlow()
        model.closeImport()
        assertTrue(job.isCancelled)
        assertNull(model.importState.value)
        old.release.countDown()
        awaitCondition { job.isCompleted }
        assertNull(model.importState.value)
    }

    @Test
    fun staleSuccessCannotReplaceANewerPreview() = checkStaleReader(fail = false)

    @Test
    fun staleFailureCannotReplaceANewerPreview() = checkStaleReader(fail = true)

    @Test
    fun staleSuccessCannotPublishEvenWithoutCancellation() = checkStaleReader(fail = false, cancel = false)

    @Test
    fun staleFailureCannotPublishEvenWithoutCancellation() = checkStaleReader(fail = true, cancel = false)

    private fun checkStaleReader(fail: Boolean, cancel: Boolean = true) {
        val (old, job) = startSlow(fail)
        if (!cancel) {
            // Isolate the operation-id guard: let the old coroutine complete normally.
            DataViewModel::class.java.getDeclaredField("importReadJob").apply { isAccessible = true }
                .set(model, null)
        }
        val uri = Uri.parse("content://test/new")
        shadowOf(application.contentResolver).registerInputStream(uri, csv("New").byteInputStream())
        model.startImport(uri)
        assertEquals(cancel, job.isCancelled)
        awaitCondition { model.importState.value is ImportState.Ready }
        assertEquals("New", (model.importState.value as ImportState.Ready).plan.workouts.single().title)
        old.release.countDown()
        awaitCondition { job.isCompleted }
        if (!cancel) assertFalse("Stale reader must complete without cancellation", job.isCancelled)
        assertEquals("New", (model.importState.value as ImportState.Ready).plan.workouts.single().title)
    }

    @Test
    fun oversizedHevyReadFailsWithTheSizeMessage() {
        val uri = Uri.parse("content://test/large-csv")
        shadowOf(application.contentResolver).registerInputStream(uri, sizedStream(25 * 1024 * 1024 + 1))
        model.startImport(uri)
        awaitCondition { model.importState.value is ImportState.Failed }
        assertEquals("This file is too large to be a Hevy export", (model.importState.value as ImportState.Failed).message)
    }

    @Test
    fun oversizedBackupReadUsesItsOwnLimitAndMessage() {
        val uri = Uri.parse("content://test/large-backup")
        shadowOf(application.contentResolver).registerInputStream(uri, sizedStream(50 * 1024 * 1024 + 1))
        model.openBackup(uri)
        awaitCondition { model.backupState.value is BackupState.Failed }
        assertEquals("This file is too large to be a Tonnage backup", (model.backupState.value as BackupState.Failed).message)
    }

    private fun sizedStream(size: Int): InputStream = object : InputStream() {
        private var remaining = size
        override fun read(): Int = if (remaining-- > 0) 'x'.code else -1
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) return -1
            val count = minOf(length, remaining)
            bytes.fill('x'.code.toByte(), offset, offset + count)
            remaining -= count
            return count
        }
    }

    private class SlowStream(bytes: ByteArray, private val fail: Boolean) : InputStream() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        private val input = ByteArrayInputStream(bytes)

        override fun read(): Int = error("Buffered read expected")
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Test reader not released" }
            if (fail) throw IOException("Old file failed")
            return input.read(bytes, offset, length)
        }
    }
}
