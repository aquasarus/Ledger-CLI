package com.millspills.ledgercli.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.millspills.ledgercli.MainActivity
import com.millspills.ledgercli.MainViewModel
import com.millspills.ledgercli.R
import com.millspills.ledgercli.aliasesDataStore
import com.millspills.ledgercli.config.AliasGroup
import com.millspills.ledgercli.dataStore
import com.millspills.ledgercli.ledgerdata.SimpleTransactionInfo
import com.millspills.ledgercli.proto.toMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.time.LocalDate

class NotificationListener : NotificationListenerService() {

    private lateinit var notificationManager: NotificationManager

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val context = this
    private val binder = LocalBinder()
    private var aliases: Map<String, AliasGroup>? = null

    inner class LocalBinder : Binder() {
        fun getService(): NotificationListener = this@NotificationListener
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == MainActivity.MAIN_BIND_ACTION) {
            binder
        } else {
            super.onBind(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(CHANNEL_ID, "Main Channel", NotificationManager.IMPORTANCE_MIN)
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel)

        // preload aliases
        scope.launch {
            aliasesDataStore.data.collect {
                Log.d(TAG, "Received updated aliases from Data Store!")
                aliases = it.toMap()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Notification listener OnStart...")

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Ledger CLI is listening")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(
            1,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        )

        return START_STICKY
    }

    @Deprecated("Made obsolete by proto data store")
    fun reloadAliases(aliases: Map<String, AliasGroup>) {
//        Log.d(TAG, "Notification listener received new aliases.")
//        this.aliases = aliases
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification

        val packageName =
            notification.extras?.getParcelable<ApplicationInfo>("android.appInfo")?.packageName
                ?: ""
        var body =
            notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        var bodyBigText =
            notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
                ?: ""
        val title =
            notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""

        // add missing 0 if needed
        body = ParseUtils.addZeroToDollarAmount(body)
        bodyBigText = ParseUtils.addZeroToDollarAmount(bodyBigText)

        val notificationRawSummary =
            "Notification from $packageName with title \"$title\", body \"$body\", big text body \"$bodyBigText\""

        val parsedTransaction = if (packageName.contains("cibc")) {
            parseCibcNotification(body)
        } else if (packageName.contains("tangerine")) {
            // use big text instead of body for tangerine
            when (title) {
                "Withdrawal made" -> parseTangerineDebitNotification(bodyBigText)
                "Direct deposit received" -> parseTangerineDirectDepositNotification(bodyBigText)
                else -> parseTangerineCreditNotification(bodyBigText)
            }
        } else if (packageName.contains("facebook")) {
            Log.d(TAG, "Found potential test notification.")
            when {
                body.startsWith(PREFIX_CIBC) -> parseCibcNotification(body.removePrefix(PREFIX_CIBC))

                body.startsWith(PREFIX_TANGERINE_CREDIT) -> parseTangerineCreditNotification(
                    body.removePrefix(PREFIX_TANGERINE_CREDIT)
                )

                body.startsWith(PREFIX_TANGERINE_DEBIT) -> parseTangerineDebitNotification(
                    body.removePrefix(PREFIX_TANGERINE_CREDIT)
                )

                body.startsWith(PREFIX_TANGERINE_DEPOSIT) -> parseTangerineDirectDepositNotification(
                    body.removePrefix(PREFIX_TANGERINE_DEPOSIT)
                )

                else -> null
            }
        } else {
            Log.d(TAG, "Ignoring notification from $packageName")
            null
        }

        parsedTransaction?.let { transaction ->
            Log.d(
                TAG, "---\n" +
                        "Found notification!\n" +
                        "Package name: $packageName\n" +
                        "Title: $title\n" +
                        "Body: $body\n" +
                        "Big Text Body: $bodyBigText\n" +
                        "---\n"
            )
            Log.d(TAG, "Parsed notification: \n$transaction\n---")

            scope.launch {
                val settings = dataStore.data.first()
                val ledgerFilePath = settings[MainActivity.LEDGER_FILE_PATH]

                if (ledgerFilePath != null) {
                    val ledgerFileUri = Uri.parse(ledgerFilePath)

                    // replace account with default Tangerine credit card if appropriate
                    val defaultTangerineCreditCard =
                        settings[MainActivity.DEFAULT_TANGERINE_CREDIT_CARD] ?: "Unknown"
                    if (transaction.account == "Tangerine") {
                        Log.d(
                            TAG,
                            "Injecting default Tangerine credit card: $defaultTangerineCreditCard"
                        )
                        transaction.account = "Liabilities:$defaultTangerineCreditCard"
                    }

                    // check if payee has a matching alias
                    aliases?.let {
                        for ((actual, aliasGroup) in it) {
                            val payeeMatcher = transaction.payee.lowercase()
                                .replace(MainViewModel.ALIAS_FORMAT, "")

                            aliasGroupLoop@ for (alias in aliasGroup.aliases) {
                                if (payeeMatcher.contains(alias)) {
                                    Log.d(
                                        TAG,
                                        "Found alias [$actual] for payee [${transaction.payee}] by matching [$alias]!"
                                    )
                                    transaction.payee = actual

                                    val (mostUsedCategory, confident) = aliasGroup.getMostUsedCategory()
                                    mostUsedCategory?.let {
                                        Log.d(
                                            TAG,
                                            "Most used category is: [$mostUsedCategory], confident: [$confident]"
                                        )
                                        transaction.category = mostUsedCategory
                                        transaction.confident = confident
                                    }

                                    break@aliasGroupLoop
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Finalized transaction:\n$transaction")
                    if (PROD_MODE) {
                        if (transaction.dollarAmount != "Unknown") {
                            val isTest = packageName.contains("facebook")
                            // only log log prod version if not test
                            if (!isTest) {
                                writeToFile(transaction, ledgerFileUri)
                            }

                            // also log debug version to separate file
                            val debugLogsPath = settings[MainActivity.DEBUG_LOGS_FILE_PATH]
                            if (debugLogsPath != null) {
                                val debugLogsUri = Uri.parse(debugLogsPath)
                                writeToFile(
                                    transaction,
                                    debugLogsUri,
                                    notificationRawSummary
                                )
                            }
                        } else {
                            Firebase.crashlytics.recordException(Exception("Failed to parse: $notificationRawSummary"))
                        }
                    }
                }
            }
        }
    }

    private fun writeToFile(
        simpleTransactionInfo: SimpleTransactionInfo,
        uri: Uri,
        rawSummary: String? = null
    ) {
        try {
            Log.d(TAG, "Writing to file...")
            val outputStream = context.contentResolver.openOutputStream(uri, "wa")
            val writer = BufferedWriter(OutputStreamWriter(outputStream))

            if (!fileEndsWithNewLine(uri)) {
                writer.appendLine()
            }

            writer.appendLine()

            writer.append(simpleTransactionInfo.toString())

            rawSummary?.let {
                writer.append("\n        ; ${rawSummary.replace("\n", "")}")
            }

            writer.close()

            outputStream?.close()
            Log.d(TAG, "Write success!")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // super weird function by ChatGPT to check if the file ends with a new line
    private fun fileEndsWithNewLine(uri: Uri): Boolean {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val fileSize = inputStream.available()
                if (fileSize > 0) {
                    inputStream.skip(fileSize.toLong() - 1) // Move to the last byte

                    val lastByte = ByteArray(1)
                    if (inputStream.read(lastByte) != -1) {
                        val lastCharacter = lastByte[0].toChar()
                        return lastCharacter == '\n' || lastCharacter == '\r'
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private fun parseCibcNotification(body: String): SimpleTransactionInfo? {
        if (ParseUtils.isCibcCreditCardPayment(body)) {
            return null // skip credit card payments
        }

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
            // use fallback method
            val dollarAmountRegex = """\$[\d.,]+""".toRegex()
            val dollarAmountMatch = dollarAmountRegex.find(body)
            val dollarAmount = dollarAmountMatch?.value ?: "Unknown"
            SimpleTransactionInfo(dollarAmount, "Unknown", LocalDate.now(), "Liabilities:Unknown")
        }
    }

    private fun parseTangerineCreditNotification(body: String): SimpleTransactionInfo {
        return try {
            val regex = """(\$[\d.,]+).*?at (.*?) on""".toRegex()
            val matchResult = regex.find(body)

            return matchResult!!.let {
                val (dollarAmount, payee) = it.destructured
                SimpleTransactionInfo(dollarAmount, payee.trim(), LocalDate.now(), "Tangerine")
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
                "Tangerine"
            )
        }
    }

    private fun parseTangerineDebitNotification(body: String): SimpleTransactionInfo {
        val dollarAmountRegex = """\$[\d.,]+""".toRegex()
        val dollarAmountMatch = dollarAmountRegex.find(body)
        val dollarAmount = dollarAmountMatch?.value ?: "Unknown"

        return SimpleTransactionInfo(
            dollarAmount,
            "Unspecified withdrawal",
            LocalDate.now(),
            "Assets:Tangerine Checking"
        )
    }

    private fun parseTangerineDirectDepositNotification(body: String): SimpleTransactionInfo {
        val dollarAmountRegex = """\$[\d.,]+""".toRegex()
        val dollarAmountMatch = dollarAmountRegex.find(body)
        val dollarAmount = dollarAmountMatch?.value ?: "Unknown"

        return SimpleTransactionInfo(
            dollarAmount,
            "Unknown",
            LocalDate.now(),
            "Income:Salary",
            category = "Assets:Tangerine Checking"
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // This method is called when a notification is removed
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    companion object {
        const val CHANNEL_ID = "CHANNEL_ID_3"
        const val PROD_MODE = true
        private const val TAG = "NotificationListener"
        private const val PREFIX_CIBC = "test.cibc:"
        private const val PREFIX_TANGERINE_CREDIT = "test.tangerine.credit:"
        private const val PREFIX_TANGERINE_DEBIT = "test.tangerine.debit"
        private const val PREFIX_TANGERINE_DEPOSIT = "test.tangerine.deposit"
    }
}
