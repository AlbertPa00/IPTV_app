package com.iptv.core.designsystem.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.iptv.core.designsystem.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyState_showsText() {
        compose.setContent { EmptyState(text = "Sin canales") }
        compose.onNodeWithText("Sin canales").assertIsDisplayed()
    }

    @Test
    fun errorState_retryInvokesCallback() {
        var retried = false
        compose.setContent { ErrorState(message = "Fallo", onRetry = { retried = true }) }
        val retry = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.ds_retry)

        compose.onNodeWithText("Fallo").assertIsDisplayed()
        compose.onNodeWithText(retry).performClick()
        assertTrue(retried)
    }
}
