package com.janreins.tonnage

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.DocumentsContract
import org.robolectric.shadows.ShadowContentResolver
import java.io.OutputStream
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.backup.BackupContents
import com.janreins.tonnage.data.backup.BackupJson
import com.janreins.tonnage.data.model.Routine
import com.janreins.tonnage.data.model.RoutineContents
import com.janreins.tonnage.data.settings.SettingsStore
import com.janreins.tonnage.data.settings.ThemeMode
import com.janreins.tonnage.data.local.AppDatabase
import com.janreins.tonnage.data.model.Exercise
import kotlinx.coroutines.runBlocking
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
import java.io.ByteArrayOutputStream
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

    @Test
    fun aRestoreRefusedByItsChecksShowsItsMessageOnce() {
        val routine = RoutineContents(Routine(id = 5, name = "Push A"), emptyList())
        val contents = BackupContents(1L, 90, emptyList(), emptyList(), listOf(routine, routine.copy(routine = routine.routine.copy(name = "Push B"))), emptyList())
        val uri = Uri.parse("content://test/duplicate-routines")
        shadowOf(application.contentResolver).registerInputStream(uri, ByteArrayInputStream(BackupJson.write(contents).toByteArray()))
        model.openBackup(uri)
        awaitCondition { model.backupState.value is BackupState.ConfirmRestore }
        model.confirmRestore()
        awaitCondition { model.backupState.value is BackupState.Failed }
        assertEquals(
            "The file has two routines with the same id. Nothing was changed.",
            (model.backupState.value as BackupState.Failed).message,
        )
    }

    @Test
    fun savingABackupRemembersWhenItWasMade() {
        val before = System.currentTimeMillis()
        val uri = Uri.parse("content://test/backup-out")
        val out = ByteArrayOutputStream()
        shadowOf(application.contentResolver).registerOutputStream(uri, out)
        model.saveBackup(uri)
        awaitCondition { model.backupState.value is BackupState.Saved }
        val saved = SettingsStore.get(application).lastBackupAt.value
        assertTrue("Last backup time $saved should be at least $before", saved != null && saved >= before)
        assertEquals(saved, BackupJson.read(out.toString(Charsets.UTF_8.name())).exportedAt)
    }


    private fun restoreText(text: String) {
        val uri = Uri.parse("content://test/settings-backup")
        shadowOf(application.contentResolver).registerInputStream(uri, ByteArrayInputStream(text.toByteArray()))
        model.openBackup(uri)
        awaitCondition { model.backupState.value is BackupState.ConfirmRestore }
        model.confirmRestore()
        awaitCondition { model.backupState.value is BackupState.Restored || model.backupState.value is BackupState.Failed }
        assertTrue("Restore failed: ${model.backupState.value}", model.backupState.value is BackupState.Restored)
    }

    @Test fun backupRoundTripRestoresThemeAndZoneOnAPhoneWithoutASavedZone() {
        val settings = SettingsStore.get(application)
        settings.setThemeMode(ThemeMode.DARK)
        settings.rememberHevyImportZone("Asia/Manila")
        val out = ByteArrayOutputStream()
        val uri = Uri.parse("content://test/settings-out")
        shadowOf(application.contentResolver).registerOutputStream(uri, out)
        model.saveBackup(uri)
        awaitCondition { model.backupState.value is BackupState.Saved }
        val text = out.toString(Charsets.UTF_8.name())
        val parsed = BackupJson.read(text)
        assertEquals(ThemeMode.DARK, parsed.themeMode)
        assertEquals("Asia/Manila", parsed.hevyImportZone)
        assertEquals(parsed, BackupJson.read(BackupJson.write(parsed)))
        assertEquals(BackupJson.SCHEMA_VERSION, org.json.JSONObject(text).getInt("schemaVersion"))
        settings.setThemeMode(ThemeMode.LIGHT)
        // Simulate a destination phone which has not imported a Hevy file yet.
        application.getSharedPreferences("settings", Application.MODE_PRIVATE).edit().remove("hevy_import_zone").commit()
        restoreText(text)
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
        assertEquals("Asia/Manila", settings.hevyImportZone)
    }

    @Test fun oldExporterFixtureKeepsThePhonesThemeAndZone() {
        val settings = SettingsStore.get(application)
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.rememberHevyImportZone("Asia/Manila")
        runBlocking {
            AppDatabase.getInstance(application).exerciseDao().insertAll(listOf(
                Exercise(id = "bench", name = "Bench", category = "strength", equipment = "barbell", primaryMuscles = "chest"),
            ))
        }
        restoreText(javaClass.getResource("/backup/current-export-v1.json")!!.readText())
        assertEquals(ThemeMode.LIGHT, settings.themeMode.value)
        assertEquals("Asia/Manila", settings.hevyImportZone)
        assertEquals(75, settings.defaultRestSeconds.value)
    }

    @Test fun restoreKeepsAnExistingImportZone() {
        val settings = SettingsStore.get(application)
        settings.rememberHevyImportZone("Asia/Manila")
        val contents = BackupContents(1L, 90, emptyList(), emptyList(), emptyList(), emptyList(), ThemeMode.DARK, "Europe/Amsterdam")
        restoreText(BackupJson.write(contents))
        assertEquals("Asia/Manila", settings.hevyImportZone)
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
    }

    @Test fun invalidImportZoneIsIgnoredWithoutFailingRestore() {
        val settings = SettingsStore.get(application)
        val contents = BackupContents(1L, 90, emptyList(), emptyList(), emptyList(), emptyList(), ThemeMode.DARK, "Invalid/Zone")
        restoreText(BackupJson.write(contents))
        assertNull(settings.hevyImportZone)
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
    }

    @Test fun unknownThemeIsIgnoredInsteadOfResettingThePhonesTheme() {
        val settings = SettingsStore.get(application)
        settings.setThemeMode(ThemeMode.LIGHT)
        val contents = BackupContents(1L, 90, emptyList(), emptyList(), emptyList(), emptyList())
        val json = org.json.JSONObject(BackupJson.write(contents))
        json.getJSONObject("settings").put("themeMode", "FUTURE_THEME")
        restoreText(json.toString())
        assertEquals(ThemeMode.LIGHT, settings.themeMode.value)
    }

    private class BackupProvider(private val refuseDelete: Boolean = false) : ContentProvider() {
        var exists = true
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/json"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) =
            MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)).apply { if (exists) addRow(arrayOf("backup.json")) }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unused")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Unused")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Use DocumentsContract")
        override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
            check(method == "android:deleteDocument")
            if (refuseDelete) throw UnsupportedOperationException("Provider refused deletion")
            exists = false
            return Bundle()
        }
    }

    private fun backupDocument(refuseDelete: Boolean = false): Uri {
        val provider = BackupProvider(refuseDelete)
        provider.attachInfo(application, ProviderInfo().apply { authority = "backup.test" })
        ShadowContentResolver.registerProviderInternal("backup.test", provider)
        return DocumentsContract.buildDocumentUri("backup.test", "backup.json")
    }

    private fun documentCount(uri: Uri) = application.contentResolver.query(uri, null, null, null, null)!!.use { it.count }

    @Test
    fun oversizedBackupExportDeletesThePickerCreatedDocument() {
        val uri = backupDocument()
        assertEquals(1, documentCount(uri))
        val lastBackup = SettingsStore.get(application).lastBackupAt.value
        // Exercise the real size check without allocating a 50 MB history in the test.
        model = DataViewModel(application) { BackupJson.encode(it, limit = 0) }
        store.put("data", model)
        model.saveBackup(uri)
        awaitCondition { model.backupState.value is BackupState.Failed }
        assertTrue((model.backupState.value as BackupState.Failed).message.contains("too large"))
        assertEquals(0, documentCount(uri))
        assertEquals(lastBackup, SettingsStore.get(application).lastBackupAt.value)
    }

    @Test
    fun failedWritesDeleteTheDocumentAndDeletionFailuresDoNotMaskTheError() {
        listOf(false, true).forEach { refuseDelete ->
            val uri = backupDocument(refuseDelete)
            shadowOf(application.contentResolver).registerOutputStream(uri, object : OutputStream() {
                override fun write(value: Int) { throw IOException("Disk full") }
            })
            model.saveBackup(uri)
            awaitCondition { model.backupState.value is BackupState.Failed }
            assertTrue((model.backupState.value as BackupState.Failed).message.contains("Disk full"))
            assertEquals(if (refuseDelete) 1 else 0, documentCount(uri))
        }
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
