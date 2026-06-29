package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputConfOverrideTest {
    @Test
    fun parseInputConfOverrideKeysKeepsOnlyExplicitBindings() {
        val keys = parseInputConfOverrideKeys(
            sequenceOf(
                "",
                "# RIGHT seek 10",
                "[section]",
                "RIGHT seek 10",
                "LEFT    no-osd seek -10"
            )
        )

        assertEquals(
            setOf(
                InputConfOverrideKey(eventKey = "RIGHT", dispatchKey = "RIGHT"),
                InputConfOverrideKey(eventKey = "LEFT", dispatchKey = "LEFT")
            ),
            keys
        )
    }

    @Test
    fun parseInputConfOverrideKeysMatchesDpadAndHexAliasesButDispatchesOriginalToken() {
        val keys = parseInputConfOverrideKeys(
            sequenceOf(
                "DPAD_LEFT seek -5",
                "0x10003 seek 5",
                "ctrl+dpad_right playlist-next"
            )
        )

        assertTrue(keys.contains(InputConfOverrideKey(eventKey = "LEFT", dispatchKey = "DPAD_LEFT")))
        assertTrue(keys.contains(InputConfOverrideKey(eventKey = "RIGHT", dispatchKey = "0x10003")))
        assertTrue(keys.contains(InputConfOverrideKey(eventKey = "CTRL+RIGHT", dispatchKey = "ctrl+dpad_right")))
    }
}
