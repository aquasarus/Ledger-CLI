package com.millspills.ledgercli.ledgerdata

data class TransactionAccount(
    val account: String,
    val amount: String?,
    val inlineComment: String?,
    var nextLineComment: String? = null
) {
    fun addNextLineComment(comment: String) {
        nextLineComment = comment
    }

    override fun toString(): String {
        val amountString = amount?.let {
            "    $amount"
        } ?: ""
        val inlineCommentString = inlineComment?.let {
            "    ; $inlineComment"
        } ?: ""
        val nextLineCommentString = nextLineComment?.let {
            "\n        ; $nextLineComment"
        } ?: ""

        return "    $account$amountString$inlineCommentString$nextLineCommentString"
    }

    companion object {
        private val regex = """^([^$]*)(?:    )?(\$[\d.,-]*)?(?:    ; (.*))?$$""".toRegex()

        fun fromString(string: String): TransactionAccount? {
            return regex.find(string)?.let {
                val (accountString, amountString, inlineCommentString) = it.destructured
                val amount = amountString.ifBlank { null }
                val inlineComment = inlineCommentString.ifBlank { null }

                if (accountString.isBlank())
                    throw Exception("Account string cannot be blank!")

                TransactionAccount(
                    accountString.trim(), amount?.trim(), inlineComment?.trim()
                )
            } ?: run {
                null
            }
        }
    }
}
