package com.millspills.ledgercli.ledgerdata

import androidx.datastore.preferences.protobuf.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TransactionTitle(
    val date: LocalDate,
    val unread: Boolean,
    val payee: String,
    val inlineComment: String?,
    var nextLineComment: String? = null
) {
    fun addNextLineComment(comment: String) {
        nextLineComment = comment
    }

    override fun toString(): String {
        val dateString = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val unreadString = if (unread) "! " else ""
        val inlineCommentString = inlineComment?.let {
            "    ; $inlineComment"
        } ?: ""
        val nextLineCommentString = nextLineComment?.let {
            "\n    ; $nextLineComment"
        } ?: ""

        return "$dateString $unreadString $payee$inlineCommentString$nextLineCommentString"
    }

//    val dateProto: Timestamp
//        get() = date!!.toProtoTimestamp()
//    private fun LocalDate.toProtoTimestamp(): Timestamp {
//        val instant = this.atStartOfDay(ZoneId.systemDefault()).toInstant()
//        return Timestamp.newBuilder()
//            .setSeconds(instant.epochSecond)
//            .setNanos(instant.nano)
//            .build()
//    }

    companion object {
        private val regex = """^(\d{4}/\d{2}/\d{2}) (! )?([^;]+)(?:    ; (.*))?$""".toRegex()

        fun fromString(string: String): TransactionTitle? {
            return regex.find(string)?.let {
                val (dateString, unreadString, payee, inlineCommentString) = it.destructured

                val localDate =
                    LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                val unread = unreadString == "! "
                val inlineComment = inlineCommentString.ifBlank { null }

                TransactionTitle(localDate, unread, payee, inlineComment)
            } ?: run {
                null
            }
        }
    }
}
