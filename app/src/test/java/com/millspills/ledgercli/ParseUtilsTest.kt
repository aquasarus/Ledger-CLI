package com.millspills.ledgercli

import com.millspills.ledgercli.notifications.ParseUtils
import org.junit.Test

import org.junit.Assert.*

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

    @Test
    fun checkCibcCreditCardPaymentTrue() {
        val testInput = "CIBC Dividend Visa Infinite Card, 1234 Payment $1,977.91"
        assertEquals(true, ParseUtils.isCibcCreditCardPayment(testInput))
    }

    @Test
    fun checkCibcCreditCardPaymentTrueExtraWhitespace() {
        val testInput = "CIBC Dividend Visa Infinite Card, 1234 Payment     $1,977.91"
        assertEquals(true, ParseUtils.isCibcCreditCardPayment(testInput))
    }

    @Test
    fun checkCibcCreditCardPaymentFalse() {
        val testInput = "CIBC Dividend Visa Infinite Card, 1234 ROB'S NF #7076 $81.62"
        assertEquals(false, ParseUtils.isCibcCreditCardPayment(testInput))
    }
}
