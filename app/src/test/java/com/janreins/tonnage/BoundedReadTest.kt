package com.janreins.tonnage

import com.janreins.tonnage.data.hevy.BACKUP_BYTE_LIMIT
import com.janreins.tonnage.data.hevy.BACKUP_TOO_LARGE
import com.janreins.tonnage.data.hevy.HEVY_BYTE_LIMIT
import com.janreins.tonnage.data.hevy.HEVY_TOO_LARGE
import com.janreins.tonnage.data.hevy.readBoundedText
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.InputStream

class BoundedReadTest {
    @Test
    fun underAndExactlyAtLimitReadFullyIncludingUtf8AcrossBuffers() {
        val text = "x".repeat(8191) + "💪\nPlank"
        val bytes = text.toByteArray()
        for (limit in listOf(bytes.size, bytes.size + 1)) {
            assertEquals(text, readBoundedText(bytes.inputStream(), limit, HEVY_TOO_LARGE))
        }
        assertEquals("", readBoundedText(byteArrayOf().inputStream(), 0, HEVY_TOO_LARGE))
    }

    @Test
    fun overLimitFailsClearlyWithoutTrustingAvailableOrReadingTheWholeStream() {
        var read = 0
        val unknownSize = object : InputStream() {
            override fun available() = 0
            override fun read(): Int { read++; return 'x'.code }
        }
        val error = assertThrows(IllegalArgumentException::class.java) {
            readBoundedText(unknownSize, 10, HEVY_TOO_LARGE)
        }
        assertEquals(HEVY_TOO_LARGE, error.message)
        assertEquals(11, read)
    }

    @Test
    fun realCsvAndBackupLimitsAreEnforcedWithoutLargeTestAllocations() {
        for ((limit, message) in listOf(HEVY_BYTE_LIMIT to HEVY_TOO_LARGE, BACKUP_BYTE_LIMIT to BACKUP_TOO_LARGE)) {
            // Supply full buffers until the boundary, then exceed it by one byte.
            val stream = object : InputStream() {
                override fun read(): Int = 'x'.code
                override fun read(bytes: ByteArray, offset: Int, length: Int) = length
            }
            assertEquals(message, assertThrows(IllegalArgumentException::class.java) {
                readBoundedText(stream, limit, message)
            }.message)
        }
        assertEquals(25 * 1024 * 1024, HEVY_BYTE_LIMIT)
        assertEquals(50 * 1024 * 1024, BACKUP_BYTE_LIMIT)
    }

    @Test
    fun cancellationIsPropagatedBetweenChunks() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            readBoundedText(ByteArray(20_000).inputStream(), 30_000, HEVY_TOO_LARGE) {
                if (++checks == 2) throw CancellationException("cancelled")
            }
        }
        assertEquals(2, checks)
    }
}
