package com.millspills.ledgercli

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.millspills.ledgercli.config.AliasGroup
import com.millspills.ledgercli.ledgerdata.LedgerFile
import com.millspills.ledgercli.ledgerdata.Transaction
import com.millspills.ledgercli.ledgerdata.TransactionAccount
import com.millspills.ledgercli.ledgerdata.TransactionTitle
import com.millspills.ledgercli.notifications.ParseUtils
import com.millspills.ledgercli.proto.toProto
import com.millspills.ledgercli.proto.toLedgerFile
import com.millspills.ledgercli.proto.toMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainViewModel(applicationContext: Context) : ViewModel() {
    private val context: Context = applicationContext

    private val _ledgerRawString = MutableLiveData<String>()
    val ledgerRawString: LiveData<String>
        get() = _ledgerRawString

    private val _ledgerFile = MutableLiveData<LedgerFile>()
    val ledgerFile: LiveData<LedgerFile>
        get() = _ledgerFile

    private val _aliases = mutableMapOf<String, AliasGroup>()
    val aliases: Map<String, AliasGroup>
        get() = _aliases

    @Deprecated("Made obsolete by proto data store")
    private val _aliasLoadedNotifier = MutableLiveData<Unit>()

    @Deprecated("Made obsolete by proto data store")
    val aliasLoadedNotifier: LiveData<Unit>
        get() = _aliasLoadedNotifier

    // No need to do the following since we already called it in MainActivity::OnResume
//    init {
//        reloadLedgerFile()
//        reloadAliasesFile()
//    }

    fun reloadFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            // Can't call these concurrently because they both update aliases
            reloadLedgerFile()
            reloadAliasesFile()
        }
    }

    private suspend fun reloadLedgerFile() {
        val filePathFlow = context.dataStore.data.map { settings ->
            settings[MainActivity.LEDGER_FILE_PATH]
        }

        val filePath = filePathFlow.first()
        if (filePath != null) {
            val uri = Uri.parse(filePath)
            val fileRaw = readFile(uri)
            parseLedgerFile(fileRaw)
            updateLedgerRawString(fileRaw)
        }
    }

    private suspend fun reloadAliasesFile() {
        val filePathFlow = context.dataStore.data.map { settings ->
            settings[MainActivity.ALIASES_FILE_PATH]
        }

        val filePath = filePathFlow.first()
        if (filePath != null) {
            Log.d(TAG, "Loading custom aliases...")
            val uri = Uri.parse(filePath)
            val fileRaw = readFile(uri).trim()

            ParseUtils.parseAliasesFileRaw(fileRaw, TAG, _aliases)

            Log.d(TAG, "All custom aliases loaded!")
            _aliasLoadedNotifier.postValue(Unit)
            writeAliasesToDataStore()
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
                val transactionAlias = title.payee.lowercase().replace(ALIAS_FORMAT, "")

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
        writeAliasesToDataStore()
        val originalLedger = LedgerFile(ledgerAliases, ledgerInitialComments, parsedTransactions)
        _ledgerFile.postValue(originalLedger)
//        Log.d(TAG, _ledgerFile.value.toString())
//        Log.d(TAG, _aliases.toString())

        Log.d(TAG, "Testing proto restoration...")

//        Log.d(TAG, "$proto")

        val ledgerProto = originalLedger.toProto()
        context.ledgerDataStore.updateData {
            ledgerProto
        }

        val newLedgerProto = context.ledgerDataStore.data.first()
        val protoToLedger = newLedgerProto.toLedgerFile()

        val newAliasesProto = context.aliasesDataStore.data.first()
        val protoToAliases = newAliasesProto.toMap()

        if (originalLedger == protoToLedger && _aliases == protoToAliases) {
            Log.d(TAG, "Restoration successful!")
        } else {
            Log.d(TAG, "Restoration corrupted! Comparing properties...")
            Log.d(TAG, "Aliases: ${protoToLedger.aliases == originalLedger.aliases}")
            Log.d(
                TAG,
                "Initial comments: ${protoToLedger.initialComments == originalLedger.initialComments}"
            )

            val originalTransactions = originalLedger.transactions
            val protoTransactions = protoToLedger.transactions
            compareLoop@ for (i in originalTransactions.indices) {
                val oTransaction = originalTransactions[i]
                val pTransaction = protoTransactions[i]

                if (oTransaction.title != pTransaction.title) {
                    Log.d(TAG, "Title difference! ${oTransaction.title} vs ${pTransaction.title}")
                    break
                }
            }
        }
    }

    private suspend fun writeAliasesToDataStore() {
        context.aliasesDataStore.updateData {
            _aliases.toProto()
        }
    }

    private fun readFile(uri: Uri): String {
        // TODO: see if there's a better way to read files in a view model
        val inputStream = context.contentResolver.openInputStream(uri)
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

    private fun updateLedgerRawString(newData: String) {
        _ledgerRawString.postValue(newData)
    }

    companion object {
        val ALIAS_FORMAT = Regex("[^a-z0-9]+")

        private const val TAG = "MainViewModel"
    }
}
