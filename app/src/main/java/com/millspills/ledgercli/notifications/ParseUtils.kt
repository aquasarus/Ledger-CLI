package com.millspills.ledgercli.notifications

import com.millspills.ledgercli.ledgerdata.SimpleTransactionInfo
import java.time.LocalDate

class ParseUtils {
    companion object {
        fun addZeroToDollarAmount(input: String): String {
            val regex = """\$\.(\d)""".toRegex() // match all instances of $.x, where x is digit

            return regex.replace(input) { matchResult ->
                val digit = matchResult.groupValues[1]
                "\$0.$digit"
            }
        }

        fun isCibcCreditCardPayment(input: String): Boolean {
            val regex =
                """Payment\s+\$""".toRegex() // match any string containing "Payment(whitespaces)$"

            return regex.containsMatchIn(input);
        }

        fun parseAmountOnly(
            body: String,
            defaultAccount: String = "Unknown",
            defaultPayee: String = "Unknown"
        ): SimpleTransactionInfo {
            val dollarAmountRegex = """\$[\d.,]+""".toRegex()
            val dollarAmountMatch = dollarAmountRegex.find(body)
            val dollarAmount = dollarAmountMatch?.value ?: "Unknown"
            return SimpleTransactionInfo(
                dollarAmount,
                defaultPayee,
                LocalDate.now(),
                defaultAccount
            )
        }

        fun parseCibcCredit(body: String): SimpleTransactionInfo {
            return try {
                val regex = """^(.*?, \d{4})\s([^$]*)\s(\$[\d.,]+)""".toRegex()
                val matchResult = regex.find(body)

                matchResult!!.let {
                    val (accountString, payeeString, amountString) = it.destructured
                    SimpleTransactionInfo(
                        amountString.trim().replace(",", ""),
                        payeeString.trim(),
                        LocalDate.now(),
                        "Liabilities:${accountString.trim().replace(",", "")}"
                    )
                }
            } catch (ex: Exception) {
                parseAmountOnly(body, "Liabilities:Unknown")
            }
        }

        fun parseTangerineCredit(body: String): SimpleTransactionInfo {
            return try {
                val regex = """(\$[\d.,]+).*?at (.*?) on""".toRegex()
                val matchResult = regex.find(body)

                return matchResult!!.let {
                    val (dollarAmount, payee) = it.destructured
                    SimpleTransactionInfo(dollarAmount, payee.trim(), LocalDate.now(), "Tangerine")
                }
            } catch (ex: Exception) {
                parseAmountOnly(body, "Tangerine")
            }
        }

        fun parseTangerinePreAuthorizedPayment(body: String): SimpleTransactionInfo {
            return try {
                val regex = """(\$[\d.,]+).*?to (.*?) has""".toRegex()
                val matchResult = regex.find(body)

                return matchResult!!.let {
                    val (dollarAmount, payee) = it.destructured
                    SimpleTransactionInfo(
                        dollarAmount,
                        payee.trim(),
                        LocalDate.now(),
                        "Tangerine Checking"
                    )
                }
            } catch (ex: Exception) {
                parseAmountOnly(body, "Tangerine Checking")
            }
        }
    }
}
