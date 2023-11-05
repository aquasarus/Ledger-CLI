package com.millspills.ledgercli

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.millspills.ledgercli.databinding.FragmentFirstBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private lateinit var binding: FragmentFirstBinding

    private val mainViewModel: MainViewModel by activityViewModels()

//    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_first, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mainViewModel = mainViewModel
        binding.lifecycleOwner = viewLifecycleOwner

        binding.saveDefaultCreditCard.setOnClickListener {
            lifecycleScope.launch {
                requireActivity().dataStore.edit { settings ->
                    settings[MainActivity.DEFAULT_TANGERINE_CREDIT_CARD] =
                        binding.editDefaultCreditCard.text.toString()
                }
            }
            val toast = "Default credit card set to \"${binding.editDefaultCreditCard.text}\""
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }

        binding.textviewFirst.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // This method is called before the text changes
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // This method is called as the text changes
                // You can perform actions based on the changing text here
            }

            override fun afterTextChanged(s: Editable?) {
                // This method is called after the text changes
                // You can perform actions based on the changed text here
                binding.textviewFirst.post {
                    binding.textScrollParent.fullScroll(View.FOCUS_DOWN)
                }
            }
        })

        binding.textviewFirst.post {
            binding.textScrollParent.fullScroll(View.FOCUS_DOWN)
        }

        lifecycleScope.launch {
            val settings = requireActivity().dataStore.data.first()
            settings[MainActivity.DEFAULT_TANGERINE_CREDIT_CARD]?.let {
                binding.editDefaultCreditCard.setText(it)
            }
        }

//        recyclerView = view.findViewById(R.id.recyclerViewTransactions)
//        recyclerView.layoutManager = ConstraintLayoutManager(this)
//        val adapter = TransactionAdapter(emptyList())
//        recyclerView.adapter = adapter
//
//        mainViewModel.ledgerFile.observe(viewLifecycleOwner) {
//            adapter.transactions = it
//        }

//        binding.buttonFirst.setOnClickListener {
////            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
//
//            val notificationManager = requireActivity().getSystemService(NotificationListenerService.NOTIFICATION_SERVICE) as NotificationManager
//
//            // TODO: only create channel once?
//            notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Test Channel", NotificationManager.IMPORTANCE_DEFAULT))
//
//            val intent = Intent(requireActivity(), MainActivity::class.java)
//            val pendingIntent: PendingIntent = PendingIntent.getActivity(requireActivity(), 0, intent, PendingIntent.FLAG_IMMUTABLE)
//
//            val notification = Notification.Builder(requireActivity(), NotificationListener.CHANNEL_ID)
//                .setContentTitle("Test notification!")
//                .setSmallIcon(R.drawable.ic_launcher_foreground)
//                .setContentIntent(pendingIntent)
//                .build()
//
//            notificationManager.notify(2, notification)
//        }
    }

//    @Composable
//    fun TransactionList(transactions: List<Transaction>) {
//        LazyColumn {
//            items(transactions) { transaction ->
//                // Replace this with your transaction item composable
//                TransactionItem(transaction)
//            }
//        }
//    }

    companion object {
        private const val CHANNEL_ID = "CHANNEL_ID_3"
    }
}
