package com.example.monitoringapp.ui.incidents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.FragmentIncidentsBinding
import com.example.monitoringapp.databinding.LayoutFeaturedIncidentBinding
import com.example.monitoringapp.utils.UiState
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IncidentsFragment : Fragment() {

    private var _binding: FragmentIncidentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: IncidentsViewModel by viewModels()

    private var lastChartLoadId: Long? = null
    private var featuredBinding: LayoutFeaturedIncidentBinding? = null

    private val adapter by lazy {
        AlertsAdapter(
            onFeaturedReady = { featuredBinding = it },
            onConfirm = { viewModel.confirm(it.id) },
            onClose = { viewModel.close(it.id) },
            onClick = { openDetail(it) }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIncidentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvIncidents.adapter = adapter
        binding.rvIncidents.itemAnimator = null
        binding.rvIncidents.setHasFixedSize(false)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_active))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_history))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                lastChartLoadId = null
                viewModel.setTab(
                    if (tab?.position == 1) IncidentTab.HISTORY else IncidentTab.ACTIVE
                )
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOffline.collect { binding.cardOffline.isVisible = it }
                }
                launch {
                    viewModel.alertsScreen.collect { state -> renderScreen(state) }
                }
                launch {
                    viewModel.actionState.collect { state ->
                        when (state) {
                            is UiState.Success -> Snackbar.make(
                                binding.root,
                                R.string.action_success,
                                Snackbar.LENGTH_SHORT
                            ).show()
                            is UiState.Error -> Snackbar.make(
                                binding.root, state.message, Snackbar.LENGTH_LONG
                            ).show()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun renderScreen(state: AlertsScreenState) {
        binding.swipeRefresh.isRefreshing = false
        binding.tvEmpty.isVisible = state.isEmpty
        binding.rvIncidents.isVisible = !state.isEmpty

        if (state.isEmpty) {
            adapter.submitList(emptyList())
            return
        }

        val incident = state.featured.incident
        if (incident != null && lastChartLoadId != incident.id) {
            lastChartLoadId = incident.id
            viewModel.loadChartFor(incident)
        }

        wireFeaturedActions()
        adapter.submitList(state.listItems)
    }

    private fun wireFeaturedActions() {
        val featured = featuredBinding ?: adapter.featuredBinding() ?: return
        featured.btnAccept.setOnClickListener {
            viewModel.alertsScreen.value.featured.incident?.let { viewModel.accept(it.id) }
        }
        featured.btnConfirm.setOnClickListener {
            viewModel.alertsScreen.value.featured.incident?.let { viewModel.confirm(it.id) }
        }
        featured.btnGraphs.setOnClickListener {
            val incident = viewModel.alertsScreen.value.featured.incident ?: return@setOnClickListener
            val query = incident.metricName ?: return@setOnClickListener
            findNavController().navigate(
                R.id.metricsFragment,
                bundleOf(
                    "incidentId" to incident.id,
                    "metricQuery" to query,
                    "threshold" to (incident.threshold ?: 0.5f)
                )
            )
        }
    }

    private fun openDetail(incident: com.example.monitoringapp.domain.model.Incident) {
        findNavController().navigate(
            R.id.action_incidents_to_detail,
            bundleOf("incidentId" to incident.id)
        )
    }

    override fun onDestroyView() {
        featuredBinding = null
        super.onDestroyView()
        _binding = null
    }
}
