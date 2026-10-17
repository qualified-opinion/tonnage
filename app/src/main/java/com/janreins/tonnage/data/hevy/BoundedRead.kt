package com.janreins.tonnage.data.hevy

import java.io.ByteArrayOutputStream
import java.io.InputStream

const val HEVY_BYTE_LIMIT = 25 * 1024 * 1024
const val BACKUP_BYTE_LIMIT = 50 * 1024 * 1024
const val HEVY_TOO_LARGE = "This file is too large to be a Hevy export"
const val BACKUP_TOO_LARGE = "This file is too large to be a Tonnage backup"

class FileTooLargeException(message: String) : Exception(message)

/** Counts actual bytes, retaining at most [limit]. One extra byte detects an oversized stream.
 * The caller owns the stream and may supply a cancellation check between reads.
 */
fun readBoundedText(
    input: InputStream,
    limit: Int,
    tooLargeMessage: String,
    checkActive: () -> Unit = {},
): String {
    require(limit >= 0)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        checkActive()
        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit.toLong() - total + 1).toInt())
        if (count < 0) break
        if (count > limit - total) throw FileTooLargeException(tooLargeMessage)
        output.write(buffer, 0, count)
        total += count
    }
    checkActive()
    return output.toString(Charsets.UTF_8.name())
}
