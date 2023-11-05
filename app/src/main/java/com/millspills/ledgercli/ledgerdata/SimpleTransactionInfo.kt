package com.millspills.ledgercli.ledgerdata

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SimpleTransactionInfo(
    val dollarAmount: String,
    var payee: String,
    val transactionDate: LocalDate,
    var account: String?,
    var category: String = "Expenses",
    var confident: Boolean = false
) {
    override fun toString(): String {
        val dateString = transactionDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val formattedDollarAmount =
            dollarAmount.replace(",", "") // remove all commas in dollar amount
        val formattedConfidence = if (confident) "" else "! "

        return "$dateString $formattedConfidence$payee\n    $category    $formattedDollarAmount\n    $account"
    }

    fun generateTestString(): String {
        val dateString = transactionDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val formattedDollarAmount =
            dollarAmount.replace(",", "") // remove all commas in dollar amount
        val formattedConfidence = if (confident) "" else "! "

        return ";$dateString $formattedConfidence$payee\n;    $category    $formattedDollarAmount\n;    $account"
    }
}
