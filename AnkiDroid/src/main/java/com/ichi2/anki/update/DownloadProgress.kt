/*
 *  Copyright (c) 2026 Deurim Fork
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.update

import java.util.Locale
import kotlin.math.ceil

/** Snapshot reported by [UpdateDownloader.download] while an update APK is fetched. */
data class DownloadProgress(
    val stage: Stage,
    val downloadedBytes: Long = 0,
    /** `null` when the server did not send a content length. */
    val totalBytes: Long? = null,
) {
    enum class Stage { CONNECTING, DOWNLOADING, VERIFYING }

    val fraction: Float?
        get() = totalBytes?.let { downloadedBytes.toFloat() / it.toFloat() }
}

/**
 * Estimates download speed from samples within the last [windowMs], so the
 * displayed speed and remaining time follow recent throughput instead of the
 * overall average.
 */
class DownloadRateEstimator(
    private val windowMs: Long = 3_000,
) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    fun record(
        nowMs: Long,
        downloadedBytes: Long,
    ) {
        samples.addLast(nowMs to downloadedBytes)
        while (samples.size > 1 && samples.first().first < nowMs - windowMs) {
            samples.removeFirst()
        }
    }

    /** `null` until the samples cover at least one second. */
    fun bytesPerSecond(): Double? {
        if (samples.size < 2) return null
        val (startMs, startBytes) = samples.first()
        val (endMs, endBytes) = samples.last()
        val elapsedMs = endMs - startMs
        if (elapsedMs < MIN_SPAN_MS) return null
        return (endBytes - startBytes) * 1000.0 / elapsedMs
    }

    /** Seconds until [totalBytes] is reached, rounded up; `null` when unknown or stalled. */
    fun etaSeconds(totalBytes: Long?): Long? {
        if (totalBytes == null) return null
        val rate = bytesPerSecond()?.takeIf { it > 0 } ?: return null
        val remaining = (totalBytes - samples.last().second).coerceAtLeast(0)
        return ceil(remaining / rate).toLong()
    }

    private companion object {
        const val MIN_SPAN_MS = 1_000L
    }
}

/** Formats a byte count as `300 B`, `512 KB`, `12.3 MB` or `1.0 GB`. */
fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> "${(bytes / kb).toLong()} KB"
        bytes < gb -> String.format(Locale.ROOT, "%.1f MB", bytes / mb)
        else -> String.format(Locale.ROOT, "%.1f GB", bytes / gb)
    }
}
