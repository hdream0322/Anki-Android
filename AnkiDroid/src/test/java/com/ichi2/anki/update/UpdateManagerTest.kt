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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun `download is blocked when Wi-Fi-only is enabled and not on Wi-Fi`() {
        assertTrue(UpdateManager.shouldBlockForWifiOnly(wifiOnlyEnabled = true, wifiConnected = false))
    }

    @Test
    fun `download is allowed when Wi-Fi-only is enabled and on Wi-Fi`() {
        assertFalse(UpdateManager.shouldBlockForWifiOnly(wifiOnlyEnabled = true, wifiConnected = true))
    }

    @Test
    fun `download is allowed when Wi-Fi-only is disabled regardless of network`() {
        assertFalse(UpdateManager.shouldBlockForWifiOnly(wifiOnlyEnabled = false, wifiConnected = false))
    }
}
