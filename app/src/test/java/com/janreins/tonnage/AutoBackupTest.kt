package com.janreins.tonnage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.backup.AutoBackup
import com.janreins.tonnage.data.backup.BackupContents
import com.janreins.tonnage.data.backup.BackupFolder
import com.janreins.tonnage.data.backup.BackupTooLargeException
import com.janreins.tonnage.data.settings.SettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutoBackupTest {

    /** An in-memory folder; [failCreate] makes writing a new file fail, [canRename] mimics providers. */
    private class FakeFolder(var canRename: Boolean = true, var failCreate: Boolean = false) : BackupFolder {
        val files = linkedMapOf<String, ByteArray>()
        override fun names() = files.keys.toList()
        override fun create(name: String, bytes: ByteArray) {
            check(name !in files)
            files[name] = ByteArray(0)
            if (failCreate) throw IOException("disk full")
            files[name] = bytes
        }
        override fun overwrite(name: String, bytes: ByteArray) { files[name] = bytes }
        override fun delete(name: String) { files.remove(name) }
        override fun rename(from: String, to: String): Boolean {
            if (!canRename) return false
            files[to] = files.remove(from)!!
            return true
        }
    }

    private lateinit var settings: SettingsStore
    private val today = LocalDate.parse("2026-10-26")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsStore(context)
    }

    private fun contents(at: Long) = BackupContents(exportedAt = at, defaultRestSeconds = 90, customExercises = emptyList(), workouts = emptyList(), routines = emptyList(), hevyMappings = emptyList())

    private suspend fun backUp(folder: FakeFolder, bytes: String, at: Long = 1L) =
        AutoBackup.run(settings, folder, today) { contents(at) to bytes.toByteArray() }

    @Test
    fun retentionKeepsTheNewestSevenAndNeverOtherFiles() {
        val autos = (1..9).map { "tonnage-auto-2026-10-0$it.json" }
        val others = listOf("tonnage-backup-2026-10-01.json", "tonnage-auto-2026-10-01.json.tmp", "notes.txt", "tonnage-auto-latest.json")
        assertEquals(listOf("tonnage-auto-2026-10-02.json", "tonnage-auto-2026-10-01.json"), AutoBackup.toDelete(others + autos.shuffled(), today))
        assertTrue(AutoBackup.toDelete(others, today).isEmpty())
    }

    @Test
    fun retentionAlsoKeepsTheNewestFileOfEachOfTheLastEightWeeks() {
        // A backup every day for 100 days up to Monday 26 October 2026.
        val names = (0L until 100L).map { AutoBackup.fileName(today.minusDays(it)) }
        val kept = (names - AutoBackup.toDelete(names.shuffled(), today).toSet()).sortedDescending()
        val newestSeven = (0L until 7L).map { AutoBackup.fileName(today.minusDays(it)) }
        // Each earlier week's Sunday, back to the week starting Monday 7 September (eight weeks with this one).
        val sundays = listOf("2026-10-18", "2026-10-11", "2026-10-04", "2026-09-27", "2026-09-20", "2026-09-13")
            .map { AutoBackup.fileName(LocalDate.parse(it)) }
        assertEquals(newestSeven + sundays, kept)
    }

    @Test
    fun weeklyBackupsSurviveAFewBusyDays() {
        // Seven backups in the last few days must not push out last week's and older ones.
        val recent = (0L until 7L).map { AutoBackup.fileName(today.minusDays(it)) }
        val older = listOf("2026-10-14", "2026-10-07", "2026-08-01").map { AutoBackup.fileName(LocalDate.parse(it)) }
        assertEquals(listOf(AutoBackup.fileName(LocalDate.parse("2026-08-01"))), AutoBackup.toDelete(recent + older, today))
    }

    @Test
    fun aSecondBackupTheSameDayOverwritesTheDaysFile() = runTest {
        val folder = FakeFolder()
        backUp(folder, "first")
        backUp(folder, "second")
        assertEquals(listOf("tonnage-auto-2026-10-26.json"), folder.names())
        assertEquals("second", String(folder.files.getValue("tonnage-auto-2026-10-26.json")))
    }

    @Test
    fun withoutRenameTheDaysFileIsWrittenDirectly() = runTest {
        val folder = FakeFolder(canRename = false)
        backUp(folder, "first")
        backUp(folder, "second")
        assertEquals(listOf("tonnage-auto-2026-10-26.json"), folder.names())
        assertEquals("second", String(folder.files.getValue("tonnage-auto-2026-10-26.json")))
    }

    @Test
    fun oldAutomaticBackupsArePrunedAfterAWrite() = runTest {
        val folder = FakeFolder()
        (10..19).forEach { folder.files["tonnage-auto-2026-10-$it.json"] = ByteArray(0) }
        folder.files["mine.json"] = ByteArray(0)
        backUp(folder, "today")
        // The newest seven stay, and 11 October as the newest of its week; 10, 12 and 13 October go.
        assertEquals(
            (listOf("mine.json", "tonnage-auto-2026-10-11.json") + (14..19).map { "tonnage-auto-2026-10-$it.json" } + "tonnage-auto-2026-10-26.json").sorted(),
            folder.names().sorted(),
        )
    }

    @Test
    fun aFailedWriteKeepsTheEarlierFileAndRecordsTheFailure() = runTest {
        val folder = FakeFolder()
        backUp(folder, "morning", at = 5L)
        folder.files["tonnage-auto-2026-10-25.json"] = "yesterday".toByteArray()
        folder.failCreate = true
        backUp(folder, "evening", at = 9L)

        assertEquals(setOf("tonnage-auto-2026-10-25.json", "tonnage-auto-2026-10-26.json"), folder.names().toSet())
        assertArrayEquals("morning".toByteArray(), folder.files.getValue("tonnage-auto-2026-10-26.json"))
        assertArrayEquals("yesterday".toByteArray(), folder.files.getValue("tonnage-auto-2026-10-25.json"))
        assertEquals(5L, settings.lastBackupAt.value)
        assertTrue(settings.autoBackupError.value!!.contains("disk full"))
    }

    @Test
    fun tooLargeIsRecordedAndSuccessClearsTheFailure() = runTest {
        val folder = FakeFolder()
        AutoBackup.run(settings, folder, today) { throw BackupTooLargeException() }
        assertTrue(settings.autoBackupError.value!!.contains("too large"))
        assertTrue(folder.names().isEmpty())

        backUp(folder, "ok", at = 42L)
        assertNull(settings.autoBackupError.value)
        assertEquals(42L, settings.lastBackupAt.value)
    }
}
