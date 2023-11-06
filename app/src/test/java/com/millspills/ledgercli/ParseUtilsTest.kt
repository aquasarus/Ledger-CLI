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
    fun addMissingZeroAfterDollarSign_EmptyString() {
        val testInput = ""
        val desiredOutput = ""
        assertEquals(desiredOutput, ParseUtils.addZeroToDollarAmount(testInput))
    }

    @Test
    fun checkCibcCreditCardPayment_True() {
        val testInput = "CIBC Dividend Visa Infinite Card, 1234 Payment $1,977.91"
        assertEquals(true, ParseUtils.isCibcCreditCardPayment(testInput))
    }

    @Test
    fun checkCibcCreditCardPayment_TrueExtraWhitespace() {
        val testInput = "CIBC Dividend Visa Infinite Card, 1234 Payment     $1,977.91"
        assertEquals(true, ParseUtils.isCibcCreditCardPayment(testInput))
    }

    @Test
    fun checkCibcCreditCardPayment_False() {
        val testInput = "CIBC Dividend Visa Infinite Card, 1234 ROB'S NF #7076 $81.62"
        assertEquals(false, ParseUtils.isCibcCreditCardPayment(testInput))
    }

    @Test
    fun parseCibcCredit() {
        val testInput = "CIBC Dividend Visa Infinite Card, 1234 WALMART STORE #4424 $123.55"
        val transaction = ParseUtils.parseCibcCredit(testInput)
        assertEquals("WALMART STORE #4424", transaction.payee)
        assertEquals("$123.55", transaction.dollarAmount)
        assertEquals("Liabilities:CIBC Dividend Visa Infinite Card 1234", transaction.account)
    }

    @Test
    fun parseCibcCredit_BadInput() {
        val testInput = "something something $12.34 blah blah"
        val transaction = ParseUtils.parseCibcCredit(testInput)
        assertEquals("Unknown", transaction.payee)
        assertEquals("$12.34", transaction.dollarAmount)
        assertEquals("Liabilities:Unknown", transaction.account)
    }

    @Test
    fun parseTangerinePreAuthorizedPayment() {
        val testInput =
            "Your $123.45 pre-authorized payment to TANGERINE CCRD has been taken from your Account ending in 1234."
        val transaction = ParseUtils.parseTangerinePreAuthorizedPayment(testInput)
        assertEquals("TANGERINE CCRD", transaction.payee)
        assertEquals("$123.45", transaction.dollarAmount)
        assertEquals("Tangerine Checking", transaction.account)
    }

    @Test
    fun parseTangerinePreAuthorizedPayment_BadInput() {
        val testInput =
            "A purchase of $7.86 was made using your Tangerine Credit Card at UBER CANADA/UBERONE on October 19, 2023."
        val transaction = ParseUtils.parseTangerinePreAuthorizedPayment(testInput)
        assertEquals("Unknown", transaction.payee)
        assertEquals("$7.86", transaction.dollarAmount)
        assertEquals("Tangerine Checking", transaction.account)
    }

    @Test
    fun parseTangerinePreAuthorizedPayment_EmptyString() {
        val testInput = ""
        val transaction = ParseUtils.parseTangerinePreAuthorizedPayment(testInput)
        assertEquals("Unknown", transaction.payee)
        assertEquals("Unknown", transaction.dollarAmount)
        assertEquals("Tangerine Checking", transaction.account)
    }

    @Test
    fun parseTangerineCredit() {
        val testInput =
            "A purchase of $35.12 was made using your Tangerine Credit Card at UBER* EATS PENDING on October 1, 2023."
        val transaction = ParseUtils.parseTangerineCredit(testInput)
        assertEquals("UBER* EATS PENDING", transaction.payee)
        assertEquals("$35.12", transaction.dollarAmount)
        assertEquals("Tangerine", transaction.account)
    }

    @Test
    fun parseTangerineCredit_BadInput() {
        val testInput = "something something $12.34 blah blah"
        val transaction = ParseUtils.parseTangerineCredit(testInput)
        assertEquals("Unknown", transaction.payee)
        assertEquals("$12.34", transaction.dollarAmount)
        assertEquals("Tangerine", transaction.account)
    }
}
