package com.millspills.ledgercli

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.millspills.ledgercli.config.AliasGroup
import com.millspills.ledgercli.ledgerdata.LedgerFile
import com.millspills.ledgercli.ledgerdata.Transaction
import com.millspills.ledgercli.ledgerdata.TransactionAccount
import com.millspills.ledgercli.ledgerdata.TransactionTitle
import com.millspills.ledgercli.proto.toLedgerFile
import com.millspills.ledgercli.proto.toMap
import com.millspills.ledgercli.proto.toProto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class AliasCoroutineWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private var _aliases = mutableMapOf<String, AliasGroup>()

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            // Perform your asynchronous work here using coroutines
            Log.d(LOG_TAG, "Starting work manager task...")
            _aliases = mutableMapOf<String, AliasGroup>()
            reloadLedgerFile()
            reloadAliasesFile()
            applicationContext.aliasesDataStore.updateData {
                _aliases.toProto()
            }
            Result.success()
        }
    }

    private suspend fun reloadLedgerFile() {
        val filePathFlow = applicationContext.dataStore.data.map { settings ->
            settings[MainActivity.LEDGER_FILE_PATH]
        }

        val filePath = filePathFlow.first()
        if (filePath != null) {
            val uri = Uri.parse(filePath)
            val fileRaw = readFile(uri)
            parseLedgerFile(fileRaw)
        }
    }

    private suspend fun reloadAliasesFile() {
        val filePathFlow = applicationContext.dataStore.data.map { settings ->
            settings[MainActivity.ALIASES_FILE_PATH]
        }

        val filePath = filePathFlow.first()
        if (filePath != null) {
            Log.d(LOG_TAG, "Loading custom aliases...")
            val uri = Uri.parse(filePath)
            val fileRaw = readFile(uri).trim()

            val aliases = fileRaw.split(Regex("\\n\\s*\\n"))
            for (alias in aliases) {
                try {
                    val lines = alias.lines()
                    lateinit var actual: String
                    for (i in lines.indices) {
                        if (i == 0) {
                            actual = lines[i]
                        } else {
                            if (lines[i].isBlank())
                                continue

                            // keep only letters, in lowercase
                            val formattedAlias =
                                lines[i].lowercase().replace(MainViewModel.ALIAS_FORMAT, "")

                            Log.d(
                                LOG_TAG,
                                "Creating custom alias for $actual: [$formattedAlias]"
                            )
                            _aliases[actual]?.addAlias(formattedAlias) ?: run {
                                _aliases[actual] = AliasGroup(mutableSetOf(formattedAlias))
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Firebase.crashlytics.recordException(Exception("Failed to parse custom alias: $alias"))
                }
            }

            Log.d(LOG_TAG, "All custom aliases loaded!")
        }
    }

    private suspend fun parseLedgerFile(fileRaw: String) {
        val transactions = fileRaw.split(Regex("\\n\\s*\\n"))
        val parsedTransactions = arrayListOf<Transaction>()
        lateinit var ledgerAliases: String
        lateinit var ledgerInitialComments: String

        transactions.forEachIndexed { transactionIndex, transactionString ->
            if (transactionIndex == 0) {
                ledgerAliases = transactionString
                return@forEachIndexed
            } else if (transactionIndex == 1) {
                ledgerInitialComments = transactionString
                return@forEachIndexed
            }

            var i = 1 // default starting index for accounts

            try {
                val lines = transactionString.trim().lines()
                val title = TransactionTitle.fromString(lines[0])!!
                if (1 in lines.indices && lines[1].startsWith("    ; ")) {
                    title.addNextLineComment(lines[1].removePrefix("    ; "))
                    i++
                }

                val transaction = Transaction(title)

                // generate alias for transaction
                val transactionAlias =
                    title.payee.lowercase().replace(MainViewModel.ALIAS_FORMAT, "")

                _aliases[title.payee]?.addAlias(transactionAlias) ?: run {
                    _aliases[title.payee] = AliasGroup(mutableSetOf(transactionAlias))
                }

                // process each account line
                while (i in lines.indices) {
                    val account = TransactionAccount.fromString(lines[i])!!
                    i++
                    if (i in lines.indices && lines[i].startsWith("        ; ")) {
                        account.addNextLineComment(lines[i].removePrefix("        ; "))
                        i++
                    }
                    transaction.addAccount(account)

                    if (account.account.contains("Expenses")) {
                        // count category for transaction
                        _aliases[title.payee]!!.countCategory(account.account)
                    }
                }

                parsedTransactions.add(transaction)
            } catch (ex: Exception) {
                Log.e(LOG_TAG, "Failed to parse: $transactionString\nReason: $ex")
                ex.printStackTrace()
            }
        }

        Log.d(LOG_TAG, "All transactions parsed!")

        val originalLedger = LedgerFile(ledgerAliases, ledgerInitialComments, parsedTransactions)
        val ledgerProto = originalLedger.toProto()
        applicationContext.ledgerDataStore.updateData {
            ledgerProto
        }

//        Log.d(LOG_TAG, "Testing proto restoration...")

//        applicationContext.aliasesDataStore.updateData {
//            _aliases.toProto()
//        }

//        val newLedgerProto = applicationContext.ledgerDataStore.data.first()
//        val protoToLedger = newLedgerProto.toLedgerFile()
//
//        val newAliasesProto = applicationContext.aliasesDataStore.data.first()
//        val protoToAliases = newAliasesProto.toMap()
//
//        if (originalLedger == protoToLedger && _aliases == protoToAliases) {
//            Log.d(LOG_TAG, "Restoration successful!")
//        } else {
//            Log.d(LOG_TAG, "Restoration corrupted! Comparing properties...")
//            Log.d(LOG_TAG, "Aliases: ${protoToLedger.aliases == originalLedger.aliases}")
//            Log.d(
//                LOG_TAG,
//                "Initial comments: ${protoToLedger.initialComments == originalLedger.initialComments}"
//            )
//
//            val originalTransactions = originalLedger.transactions
//            val protoTransactions = protoToLedger.transactions
//            compareLoop@ for (i in originalTransactions.indices) {
//                val oTransaction = originalTransactions[i]
//                val pTransaction = protoTransactions[i]
//
//                if (oTransaction.title != pTransaction.title) {
//                    Log.d(
//                        LOG_TAG,
//                        "Title difference! ${oTransaction.title} vs ${pTransaction.title}"
//                    )
//                    break
//                }
//            }
//        }
    }

    private fun readFile(uri: Uri): String {
        val inputStream = applicationContext.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            stringBuilder.appendLine(line)
        }

        val fileContent = stringBuilder.toString()
        inputStream?.close()

        return fileContent
    }

    companion object {
        const val LOG_TAG = "AliasCoroutineWorker"
    }
}
