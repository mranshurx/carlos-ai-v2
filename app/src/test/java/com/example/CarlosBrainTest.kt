package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CarlosActionType
import com.example.engine.CarlosBrain
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
class CarlosBrainTest {

    private lateinit var brain: CarlosBrain

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        brain = CarlosBrain(context)
    }

    @Test
    fun `wake word matching works correctly`() {
        val (matched1, cmd1) = brain.matchWakeWord("Hey Carlos open whatsapp", "Hey Carlos")
        assertTrue(matched1)
        assertEquals("open whatsapp", cmd1)

        val (matched2, cmd2) = brain.matchWakeWord("Carlos turn on flashlight", "Carlos")
        assertTrue(matched2)
        assertEquals("turn on flashlight", cmd2)

        val (matched3, cmd3) = brain.matchWakeWord("hey carlos", "Hey Carlos")
        assertTrue(matched3)
        assertEquals("", cmd3)

        val (matched4, _) = brain.matchWakeWord("what is the weather today", "Hey Carlos")
        assertTrue(!matched4)
    }

    @Test
    fun `instant command parsing identifies whatsapp actions`() = runBlocking {
        val result = brain.processCommand("open whatsapp")
        assertEquals(CarlosActionType.OPEN_APP, result.actionType)
        assertEquals("whatsapp", result.target)
    }

    @Test
    fun `instant command parsing identifies camera actions`() = runBlocking {
        val result = brain.processCommand("open camera")
        assertEquals(CarlosActionType.OPEN_CAMERA, result.actionType)
    }

    @Test
    fun `instant command parsing identifies flashlight actions`() = runBlocking {
        val result1 = brain.processCommand("turn on flashlight")
        assertEquals(CarlosActionType.TOGGLE_FLASHLIGHT, result1.actionType)
        assertEquals("on", result1.target)

        val result2 = brain.processCommand("turn off flashlight")
        assertEquals(CarlosActionType.TOGGLE_FLASHLIGHT, result2.actionType)
        assertEquals("off", result2.target)

        val result3 = brain.processCommand("torch on")
        assertEquals(CarlosActionType.TOGGLE_FLASHLIGHT, result3.actionType)
        assertEquals("on", result3.target)

        val result4 = brain.processCommand("switch on the light")
        assertEquals(CarlosActionType.TOGGLE_FLASHLIGHT, result4.actionType)
        assertEquals("on", result4.target)

        val result5 = brain.processCommand("turn off my flashlight")
        assertEquals(CarlosActionType.TOGGLE_FLASHLIGHT, result5.actionType)
        assertEquals("off", result5.target)

        val result6 = brain.processCommand("turn off my flashligjt")
        assertEquals(CarlosActionType.TOGGLE_FLASHLIGHT, result6.actionType)
        assertEquals("off", result6.target)
    }

    @Test
    fun `instant command parsing identifies battery actions`() = runBlocking {
        val result = brain.processCommand("check battery")
        assertEquals(CarlosActionType.CHECK_BATTERY, result.actionType)
    }

    @Test
    fun `instant command parsing identifies phone call actions`() = runBlocking {
        val result = brain.processCommand("make a call to 1234567890")
        assertEquals(CarlosActionType.MAKE_CALL, result.actionType)
        assertEquals("1234567890", result.target)
    }
}
