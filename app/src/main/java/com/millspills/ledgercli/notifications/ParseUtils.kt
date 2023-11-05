package com.millspills.ledgercli.notifications

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
    }
}
