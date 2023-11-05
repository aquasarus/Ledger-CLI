package com.millspills.ledgercli.ledgerdata

data class Transaction(
    val title: TransactionTitle,
    val accounts: MutableList<TransactionAccount> = arrayListOf()
) {
    override fun toString(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine(title)
        for (account in accounts) {
            stringBuilder.appendLine(account)
        }
        return stringBuilder.toString()
    }

    fun addAccount(transactionAccount: TransactionAccount) {
        accounts.add(transactionAccount)
    }
}
