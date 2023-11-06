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
import com.millspills.ledgercli.notifications.ParseUtils
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
            Log.d(TAG, "Starting work manager task...")
            _aliases = mutableMapOf()
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
            Log.d(TAG, "Loading custom aliases...")
            val uri = Uri.parse(filePath)
            val fileRaw = readFile(uri).trim()

            ParseUtils.parseAliasesFileRaw(fileRaw, TAG, _aliases)

            Log.d(TAG, "All custom aliases loaded!")
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
                Log.e(TAG, "Failed to parse: $transactionString\nReason: $ex")
                ex.printStackTrace()
            }
        }

        Log.d(TAG, "All transactions parsed!")

        val originalLedger = LedgerFile(ledgerAliases, ledgerInitialComments, parsedTransactions)
        val ledgerProto = originalLedger.toProto()
        applicationContext.ledgerDataStore.updateData {
            ledgerProto
        }
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
        const val TAG = "AliasCoroutineWorker"
    }
}
