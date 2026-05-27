package com.example.monitoringapp.ui.incidents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.FragmentIncidentsBinding
import com.example.monitoringapp.databinding.LayoutFeaturedIncidentBinding
import com.example.monitoringapp.domain.model.Incident
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

    private var featuredBinding: LayoutFeaturedIncidentBinding? = null

    private val adapter by lazy {
        AlertsAdapter(
            currentUsername = { viewModel.currentUsername },
            onFeaturedReady = { featuredBinding = it },
            onAccept = { viewModel.accept(it.id) },
            onComplete = { viewModel.complete(it.id) },
            onClose = { showCloseDialog(it) },
            onSelect = { incident ->
                viewModel.selectIncident(incident)
                binding.rvIncidents.smoothScrollToPosition(0)
            },
            onOpenDetail = { openDetail(it) }
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
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        binding.header.tvHeaderTitle.setText(R.string.alerts_title)
        viewModel.objectFilterLabel?.let { label ->
            binding.header.tvHeaderSubtitle.isVisible = true
            binding.header.tvHeaderSubtitle.text = getString(R.string.alerts_filtered_object, label)
        }

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_active))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_in_progress))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tab_history))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.setTab(
                    when (tab?.position) {
                        1 -> IncidentTab.IN_PROGRESS
                        2 -> IncidentTab.HISTORY
                        else -> IncidentTab.ACTIVE
                    }
                )
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        binding.etSearch.doAfterTextChanged { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOffline.collect { binding.cardOffline.isVisible = it }
                }
                launch {
                    viewModel.tab.collect { tab ->
                        val index = when (tab) {
                            IncidentTab.IN_PROGRESS -> 1
                            IncidentTab.HISTORY -> 2
                            else -> 0
                        }
                        if (binding.tabLayout.selectedTabPosition != index) {
                            binding.tabLayout.getTabAt(index)?.select()
                        }
                    }
                }
                launch {
                    viewModel.alertsScreen.collect { state -> renderScreen(state) }
                }
                launch {
                    viewModel.promptCloseIncidentId.collect { id ->
                        val incident = viewModel.getIncidentById(id) ?: return@collect
                        showCloseDialog(incident)
                    }
                }
                launch {
                    viewModel.actionState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val messageRes = when (state.data) {
                                    "ACCEPTED" -> R.string.action_accepted
                                    "COMPLETED" -> R.string.action_completed
                                    "CLOSED" -> R.string.action_closed
                                    else -> R.string.action_success
                                }
                                Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
                            }
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
        if (incident != null &&
            state.featured.chart.points.isEmpty() &&
            !state.featured.chartLoading
        ) {
            viewModel.loadChartFor(incident)
        }

        adapter.submitList(state.listItems) {
            wireFeaturedActions()
            rebindFeaturedChart(state.featured)
        }
    }

    private fun rebindFeaturedChart(featured: FeaturedIncidentUi) {
        val incident = featured.incident ?: return
        val target = featuredBinding ?: adapter.featuredBinding() ?: return
        IncidentUiHelper.bindChart(
            target,
            incident,
            featured.chart.points,
            featured.chart.threshold,
            featured.chartLoading
        )
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        val featured = viewModel.alertsScreen.value.featured
        if (featured.incident != null) {
            binding.rvIncidents.post {
                rebindFeaturedChart(featured)
                wireFeaturedActions()
            }
        }
    }

    private fun wireFeaturedActions() {
        val featured = featuredBinding ?: adapter.featuredBinding() ?: return
        val incident = viewModel.alertsScreen.value.featured.incident ?: return
        IncidentUiHelper.bindActions(featured, incident, viewModel.currentUsername)
        featured.btnAccept.setOnClickListener { viewModel.accept(incident.id) }
        featured.btnConfirm.setOnClickListener { viewModel.complete(incident.id) }
        featured.btnClose.setOnClickListener { showCloseDialog(incident) }
        featured.btnGraphs.setOnClickListener { openFeaturedGraphs() }
        featured.chartMiniContainer.setOnClickListener { openFeaturedGraphs() }
    }

    private fun showCloseDialog(incident: Incident) {
        IncidentCloseDialog.show(requireContext(), incident) { comment ->
            viewModel.close(incident.id, comment)
        }
    }

    private fun openFeaturedGraphs() {
        val incident = viewModel.alertsScreen.value.featured.incident ?: return
        val query = incident.metricName ?: return
        findNavController().navigate(
            R.id.metricsFragment,
            bundleOf(
                "incidentId" to incident.id,
                "metricQuery" to query,
                "threshold" to (incident.threshold ?: 0.5f)
            )
        )
    }

    private fun openDetail(incident: Incident) {
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
