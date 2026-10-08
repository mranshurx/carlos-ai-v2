package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.CarlosKeyManager
import com.example.data.auth.KeyValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CarlosKeyManagerTest {

    private lateinit var keyManager: CarlosKeyManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        keyManager = CarlosKeyManager(context)
        keyManager.clearSavedKey()
    }

    @Test
    fun `empty key validation returns invalid`() = runBlocking {
        val result = keyManager.validateKeyOnline("")
        assertTrue(result is KeyValidationResult.InvalidKey)
    }

    @Test
    fun `saved key storage works properly`() {
        keyManager.savedKey = "TEST-KEY-123"
        assertEquals("TEST-KEY-123", keyManager.savedKey)

        keyManager.clearSavedKey()
        assertEquals("", keyManager.savedKey)
    }
}
