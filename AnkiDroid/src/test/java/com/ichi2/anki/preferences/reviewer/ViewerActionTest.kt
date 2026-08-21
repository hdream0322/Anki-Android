// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences.reviewer

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test

class ViewerActionTest {
    @Test
    fun `AI_CHAT is menu-only by default`() {
        assertThat(ViewerAction.AI_CHAT.defaultDisplayType, `is`(MenuDisplayType.MENU_ONLY))
    }
}
