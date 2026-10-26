package com.janreins.tonnage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.janreins.tonnage.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BodyWeightDaoTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.bodyWeightDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun savingTheSameDayTwiceKeepsOneRowWithTheNewerWeight() = runTest {
        dao.upsert(epochDay = 100, weightKg = 82.4, now = 1)
        val id = dao.all().single().id
        dao.upsert(epochDay = 100, weightKg = 81.9, now = 2)
        dao.upsert(epochDay = 99, weightKg = 83.0, now = 3)

        assertEquals(listOf(99L to 83.0, 100L to 81.9), dao.observeAll().first().map { it.epochDay to it.weightKg })
        assertEquals(id, dao.all().single { it.epochDay == 100L }.id)

        dao.delete(id)
        assertEquals(listOf(99L), dao.all().map { it.epochDay })
    }
}
