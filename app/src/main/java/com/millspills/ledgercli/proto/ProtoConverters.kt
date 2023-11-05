package com.millspills.ledgercli.proto

import Ledger
import com.google.protobuf.Timestamp
import com.millspills.ledgercli.config.AliasGroup
import com.millspills.ledgercli.ledgerdata.LedgerFile
import com.millspills.ledgercli.ledgerdata.Transaction
import com.millspills.ledgercli.ledgerdata.TransactionAccount
import com.millspills.ledgercli.ledgerdata.TransactionTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun LedgerFile.toProto(): Ledger.LedgerFileProto {
    val ledgerFileProtoBuilder = Ledger.LedgerFileProto.newBuilder()

    ledgerFileProtoBuilder.aliases = this.aliases
    ledgerFileProtoBuilder.initialComments = this.initialComments

    for (transaction in this.transactions) {
        val transactionBuilder = Ledger.Transaction.newBuilder()
        transactionBuilder.title = transaction.title.toProto()
        transactionBuilder.addAllAccounts(transaction.accounts.map { it.toProto() })
        ledgerFileProtoBuilder.addTransactions(transactionBuilder.build())
    }

    return ledgerFileProtoBuilder.build()
}

fun TransactionTitle.toProto(): Ledger.TransactionTitle {
    val transactionTitleProtoBuilder = Ledger.TransactionTitle.newBuilder()
    transactionTitleProtoBuilder.date = this.date.toProtoTimestamp()
    transactionTitleProtoBuilder.unread = this.unread
    this.payee?.let { transactionTitleProtoBuilder.payee = it }
    this.inlineComment?.let { transactionTitleProtoBuilder.inlineComment = it }
    this.nextLineComment?.let { transactionTitleProtoBuilder.nextLineComment = it }
    return transactionTitleProtoBuilder.build()
}

fun TransactionAccount.toProto(): Ledger.TransactionAccount {
    val transactionAccountProtoBuilder = Ledger.TransactionAccount.newBuilder()
    transactionAccountProtoBuilder.account = this.account
    this.amount?.let { transactionAccountProtoBuilder.amount = it }
    this.inlineComment?.let { transactionAccountProtoBuilder.inlineComment = it }
    this.nextLineComment?.let { transactionAccountProtoBuilder.nextLineComment = it }
    return transactionAccountProtoBuilder.build()
}

fun LocalDate.toProtoTimestamp(): Timestamp {
    val instant = this.atStartOfDay().toInstant(ZoneOffset.UTC)
    return Timestamp.newBuilder()
        .setSeconds(instant.epochSecond)
        .setNanos(instant.nano)
        .build()
}

fun Ledger.LedgerFileProto.toLedgerFile(): LedgerFile {
    val transactionsList = mutableListOf<Transaction>()

    for (protoTransaction in this.transactionsList) {
        val transaction = protoTransaction.toTransaction()
        transactionsList.add(transaction)
    }

    return LedgerFile(
        aliases = this.aliases,
        initialComments = this.initialComments,
        transactions = transactionsList
    )
}

fun Ledger.Transaction.toTransaction(): Transaction {
    val accountsList = mutableListOf<TransactionAccount>()

    for (protoAccount in this.accountsList) {
        val account = protoAccount.toTransactionAccount()
        accountsList.add(account)
    }

    return Transaction(
        title = this.title.toTransactionTitle(),
        accounts = accountsList
    )
}

fun Ledger.TransactionTitle.toTransactionTitle(): TransactionTitle {
    return TransactionTitle(
        date = this.date.toLocalDate(),
        unread = this.unread,
        payee = this.payee,
        inlineComment = this.inlineComment.ifBlank { null },
        nextLineComment = this.nextLineComment.ifBlank { null }
    )
}

fun Ledger.TransactionAccount.toTransactionAccount(): TransactionAccount {
    return TransactionAccount(
        account = this.account,
        amount = this.amount.ifBlank { null },
        inlineComment = this.inlineComment.ifBlank { null },
        nextLineComment = this.nextLineComment.ifBlank { null }
    )
}

fun Timestamp.toLocalDate(): LocalDate {
    val instant = Instant.ofEpochSecond(seconds, nanos.toLong())
    return instant.atZone(ZoneOffset.UTC).toLocalDate()
}

fun Alias.AliasGroupProto.toAliasGroup(): AliasGroup {
    val aliasesSet = mutableSetOf<String>()
    aliasesSet.addAll(aliasesList)

    val aliasGroup = AliasGroup(aliasesSet)
    for ((category, count) in this.categoriesMap) {
        aliasGroup.countCategory(category, count)
    }

    return aliasGroup
}

fun AliasGroup.toAliasGroupProto(): Alias.AliasGroupProto {
    val aliasGroupProtoBuilder = Alias.AliasGroupProto.newBuilder()

    aliasGroupProtoBuilder.addAllAliases(aliases)
    aliasGroupProtoBuilder.putAllCategories(categories)

    return aliasGroupProtoBuilder.build()
}

fun Alias.AliasGroupsMap.toMap(): Map<String, AliasGroup> {
    val aliasGroupsMap = mutableMapOf<String, AliasGroup>()

    for ((key, value) in this.aliasGroupsMap) {
        aliasGroupsMap[key] = value.toAliasGroup()
    }

    return aliasGroupsMap
}

fun Map<String, AliasGroup>.toProto(): Alias.AliasGroupsMap {
    val aliasGroupsMapBuilder = Alias.AliasGroupsMap.newBuilder()

    for ((key, value) in this) {
        aliasGroupsMapBuilder.putAliasGroups(key, value.toAliasGroupProto())
    }

    return aliasGroupsMapBuilder.build()
}
