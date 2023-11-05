package com.millspills.ledgercli

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.millspills.ledgercli.databinding.ActivityMainBinding
import com.millspills.ledgercli.proto.AliasSerializer
import com.millspills.ledgercli.proto.LedgerFileSerializer
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val Context.ledgerDataStore: DataStore<Ledger.LedgerFileProto> by dataStore(
    fileName = "ledger.pb",
    serializer = LedgerFileSerializer
)
val Context.aliasesDataStore: DataStore<Alias.AliasGroupsMap> by dataStore(
    fileName = "aliases.pb",
    serializer = AliasSerializer
)

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var mainViewModel: MainViewModel

    private var notificationListener: NotificationListener? = null
    private var waitingForListenerToReceiveAliases = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModelFactory = MainViewModelFactory(application)
        mainViewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            mainViewModel.reloadFiles()
        }

        startNotificationListenerService()

        // bind with notification listener service in order to call its functions
        val intent = Intent(this, NotificationListener::class.java)
        intent.action = MAIN_BIND_ACTION
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        mainViewModel.aliasLoadedNotifier.observe(this) {
            Log.d(TAG, "Alias data changed, propagating to notification listener...")
            notificationListener?.reloadAliases(mainViewModel.aliases) ?: run {
                Log.d(TAG, "Notification listener not ready yet.")
                waitingForListenerToReceiveAliases = true
            }
        }

        // Schedule alias worker to run
        val workRequest = PeriodicWorkRequest.Builder(AliasCoroutineWorker::class.java, 60, TimeUnit.MINUTES)
            .setInitialDelay(60, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "unique_alias_work",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            workRequest
        )
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.reloadFiles()
    }

    @Deprecated("Made obsolete by proto data store")
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NotificationListener.LocalBinder
            binder.getService().let {
                Log.d(TAG, "Binding notification service to MainActivity")
                notificationListener = it
                if (waitingForListenerToReceiveAliases) {
                    Log.d(TAG, "There are aliases waiting to be loaded!")
                    it.reloadAliases(mainViewModel.aliases)
                    waitingForListenerToReceiveAliases = false
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            notificationListener = null
        }
    }

    private fun startNotificationListenerService() {
        Log.d(TAG, "Starting notification listener service...")
        val intent = Intent(this, NotificationListener::class.java)
        applicationContext.startForegroundService(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_set_ledger_path -> {
                ledgerPathSetter.launch(arrayOf("*/*"))
                true
            }

            R.id.action_set_aliases_path -> {
                aliasesPathSetter.launch(arrayOf("*/*"))
                true
            }

            R.id.action_set_debug_logs_path -> {
                debugLogsPathSetter.launch(arrayOf("*/*"))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private val ledgerPathSetter =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(uri, TAKE_FLAGS)
                lifecycleScope.launch {
                    dataStore.edit { settings ->
                        settings[LEDGER_FILE_PATH] = uri.toString()
                    }
                    mainViewModel.reloadFiles()
                }
            }
        }

    private val aliasesPathSetter =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(uri, TAKE_FLAGS)
                lifecycleScope.launch {
                    dataStore.edit { settings ->
                        settings[ALIASES_FILE_PATH] = uri.toString()
                    }
                    mainViewModel.reloadFiles()
                }
            }
        }

    private val debugLogsPathSetter =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(uri, TAKE_FLAGS)
                lifecycleScope.launch {
                    dataStore.edit { settings ->
                        settings[DEBUG_LOGS_FILE_PATH] = uri.toString()
                    }
                }
            }
        }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val TAKE_FLAGS: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val LEDGER_FILE_PATH = stringPreferencesKey("ledger_file_path")
        val ALIASES_FILE_PATH = stringPreferencesKey("aliases_file_path")
        val DEBUG_LOGS_FILE_PATH = stringPreferencesKey("debug_logs_file_path")

        val DEFAULT_TANGERINE_CREDIT_CARD = stringPreferencesKey("default_credit_card")

        const val MAIN_BIND_ACTION = "main_activity_bind"
    }
}
