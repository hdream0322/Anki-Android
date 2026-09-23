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
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun `rate is unknown until the samples span at least one second`() {
        val estimator = DownloadRateEstimator()
        estimator.record(nowMs = 0, downloadedBytes = 0)
        estimator.record(nowMs = 500, downloadedBytes = 1_000_000)
        assertNull(estimator.bytesPerSecond())
        assertNull(estimator.etaSeconds(totalBytes = 10_000_000))
    }

    @Test
    fun `rate is bytes over elapsed time in the window`() {
        val estimator = DownloadRateEstimator()
        estimator.record(nowMs = 0, downloadedBytes = 0)
        estimator.record(nowMs = 1_000, downloadedBytes = 1_000_000)
        estimator.record(nowMs = 2_000, downloadedBytes = 2_000_000)
        assertEquals(1_000_000.0, estimator.bytesPerSecond()!!, 0.01)
    }

    @Test
    fun `rate only uses samples from the recent window`() {
        val estimator = DownloadRateEstimator(windowMs = 3_000)
        // 처음엔 느리다가 빨라진 경우 — 오래된 느린 구간은 버려야 한다.
        estimator.record(nowMs = 0, downloadedBytes = 0)
        estimator.record(nowMs = 5_000, downloadedBytes = 500_000)
        estimator.record(nowMs = 7_000, downloadedBytes = 4_500_000)
        estimator.record(nowMs = 8_000, downloadedBytes = 6_500_000)
        // 윈도 [5000, 8000]: 6_000_000 bytes / 3 s
        assertEquals(2_000_000.0, estimator.bytesPerSecond()!!, 0.01)
    }

    @Test
    fun `eta is remaining bytes over rate rounded up`() {
        val estimator = DownloadRateEstimator()
        estimator.record(nowMs = 0, downloadedBytes = 0)
        estimator.record(nowMs = 2_000, downloadedBytes = 4_000_000)
        // 남은 5_000_000 bytes / 2_000_000 B/s = 2.5 s → 3 s
        assertEquals(3L, estimator.etaSeconds(totalBytes = 9_000_000))
    }

    @Test
    fun `eta is unknown when total size is unknown`() {
        val estimator = DownloadRateEstimator()
        estimator.record(nowMs = 0, downloadedBytes = 0)
        estimator.record(nowMs = 2_000, downloadedBytes = 4_000_000)
        assertNull(estimator.etaSeconds(totalBytes = null))
    }

    @Test
    fun `eta is unknown when download stalls`() {
        val estimator = DownloadRateEstimator()
        estimator.record(nowMs = 0, downloadedBytes = 1_000)
        estimator.record(nowMs = 2_000, downloadedBytes = 1_000)
        assertNull(estimator.etaSeconds(totalBytes = 9_000_000))
    }

    @Test
    fun `formatBytes picks a readable unit`() {
        assertEquals("300 B", formatBytes(300))
        assertEquals("512 KB", formatBytes(512 * 1024))
        assertEquals("12.3 MB", formatBytes((12.3 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
    }
}
