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

import com.ichi2.anki.update.UpdateManager.PendingInstallAction
import org.junit.Assert.assertEquals
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

    @Test
    fun `no pending install means nothing to do`() {
        assertEquals(PendingInstallAction.NONE, pendingAction(pendingTag = null))
    }

    @Test
    fun `pending install is cleared once that version is installed`() {
        assertEquals(PendingInstallAction.CLEAR, pendingAction(pendingTag = "v0.1.8", currentTag = "v0.1.8"))
    }

    @Test
    fun `pending install is cleared when the downloaded APK is gone`() {
        assertEquals(PendingInstallAction.CLEAR, pendingAction(apkExists = false))
    }

    @Test
    fun `pending install is kept on dev builds where versions cannot be compared`() {
        assertEquals(PendingInstallAction.NONE, pendingAction(currentTag = ""))
    }

    @Test
    fun `pending install is offered once per session`() {
        assertEquals(PendingInstallAction.OFFER, pendingAction(alreadyOffered = false))
        assertEquals(PendingInstallAction.NONE, pendingAction(alreadyOffered = true))
    }

    @Test
    fun `pending install is offered again after returning from the installer`() {
        assertEquals(PendingInstallAction.OFFER, pendingAction(returnedFromInstaller = true, alreadyOffered = true))
    }

    private fun pendingAction(
        pendingTag: String? = "v0.1.8",
        currentTag: String = "v0.1.7",
        apkExists: Boolean = true,
        returnedFromInstaller: Boolean = false,
        alreadyOffered: Boolean = false,
    ) = UpdateManager.pendingInstallAction(pendingTag, currentTag, apkExists, returnedFromInstaller, alreadyOffered)
}
