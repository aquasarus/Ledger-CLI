package com.millspills.ledgercli.ledgerdata

data class LedgerFile(
    val aliases: String,
    val initialComments: String,
    val transactions: MutableList<Transaction> = arrayListOf()
) {
    override fun toString(): String {
        val stringBuilder = StringBuilder()

        // Add header stuff
        stringBuilder.appendLine(aliases)
        stringBuilder.appendLine()
        stringBuilder.appendLine(initialComments)
        stringBuilder.appendLine()

        for (transaction in transactions) {
            stringBuilder.append(transaction)
            stringBuilder.appendLine()
        }

        return stringBuilder.toString()
    }
}
