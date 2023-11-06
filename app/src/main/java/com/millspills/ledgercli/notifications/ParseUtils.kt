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
            val regex = """Payment\s+\$""".toRegex() // match any string containing "Payment(whitespaces)$"

            return regex.containsMatchIn(input);
        }

        fun parseTangerinePreAuthorizedPayment(body: String): SimpleTransactionInfo {
            return try {
                val regex = """(\$[\d.,]+).*?to (.*?) has""".toRegex()
                val matchResult = regex.find(body)

                return matchResult!!.let {
                    val (dollarAmount, payee) = it.destructured
                    SimpleTransactionInfo(dollarAmount, payee.trim(), LocalDate.now(), "Tangerine Checking")
                }
            } catch (ex: Exception) {
                // use fallback method
                val dollarAmountRegex = """\$[\d.,]+""".toRegex()
                val dollarAmountMatch = dollarAmountRegex.find(body)
                val dollarAmount = dollarAmountMatch?.value ?: "Unknown"
                SimpleTransactionInfo(
                    dollarAmount,
                    "Unknown",
                    LocalDate.now(),
                    "Tangerine Checking"
                )
            }
        }
    }
}
