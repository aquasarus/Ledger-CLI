package com.millspills.ledgercli

import com.millspills.ledgercli.notifications.ParseUtils
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ParseUtilsTest {
    @Test
    fun addMissingZeroAfterDollarSign() {
        val testInput = "Something something $.12, $1, $1.0, ending sentence with $."
        val desiredOutput = "Something something $0.12, $1, $1.0, ending sentence with $."
        assertEquals(desiredOutput, ParseUtils.addZeroToDollarAmount(testInput))
    }

    @Test
    fun addMissingZeroAfterDollarSignEmptyString() {
        val testInput = ""
        val desiredOutput = ""
        assertEquals(desiredOutput, ParseUtils.addZeroToDollarAmount(testInput))
    }
}
