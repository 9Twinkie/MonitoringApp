package com.example.monitoringapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.FragmentDashboardBinding
import com.example.monitoringapp.utils.UiState
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private val adapter = MonitoringObjectAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvObjects.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOffline.collect { offline ->
                        binding.cardOffline.isVisible = offline
                    }
                }
                launch {
                    viewModel.warning.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                launch {
                    viewModel.summary.collect { state ->
                        binding.swipeRefresh.isRefreshing = state is UiState.Loading
                        binding.progressBar.isVisible =
                            state is UiState.Loading && adapter.itemCount == 0
                        when (state) {
                            is UiState.Loading -> {
                                binding.tvServersStatus.text = getString(R.string.loading)
                            }
                            is UiState.Success -> {
                                val data = state.data
                                binding.tvCriticalCount.text = data.criticalCount.toString()
                                binding.tvServersStatus.text = if (data.serversTotal > 0) {
                                    "${data.serversOnline}/${data.serversTotal} online"
                                } else {
                                    getString(R.string.servers_status_unknown)
                                }
                                adapter.submitList(data.objects)
                                binding.tvEmptyObjects.isVisible = data.objects.isEmpty()
                            }
                            is UiState.Error -> {
                                binding.tvServersStatus.text =
                                    getString(R.string.servers_status_unknown)
                                Snackbar.make(
                                    binding.root, state.message, Snackbar.LENGTH_LONG
                                ).show()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
