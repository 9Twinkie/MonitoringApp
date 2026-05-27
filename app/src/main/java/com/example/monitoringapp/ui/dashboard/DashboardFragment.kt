package com.example.monitoringapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.FragmentDashboardBinding
import com.example.monitoringapp.domain.model.MonitoringObject
import com.example.monitoringapp.ui.main.MainActivity
import com.example.monitoringapp.utils.UiState
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var adapter: MonitoringObjectAdapter
    private var lastObjectsSignature: String? = null
    private var lastServersStatusText: String? = null
    private var lastServersStatusColor: Int? = null
    private var lastCriticalCount: Int? = null
    private var lastObjectsTitle: String? = null

    private val favoritesChipListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        viewModel.setFavoritesOnly(checked)
    }

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

        adapter = MonitoringObjectAdapter(
            onFavoriteClick = { viewModel.toggleFavorite(it) },
            onObjectClick = { item ->
                (activity as? MainActivity)?.openAlertsForObject(item.host, item.name)
            }
        )
        binding.rvObjects.adapter = adapter
        binding.rvObjects.itemAnimator = null

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        binding.etSearch.doAfterTextChanged { editable ->
            viewModel.setSearchQuery(editable?.toString().orEmpty())
        }

        binding.chipFavoritesOnly.setOnCheckedChangeListener(favoritesChipListener)

        binding.header.tvHeaderTitle.setText(R.string.dashboard_title)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isOffline.collect { binding.cardOffline.isVisible = it }
                }
                launch {
                    viewModel.isRefreshing.collect { binding.swipeRefresh.isRefreshing = it }
                }
                launch {
                    viewModel.warning.collect { message ->
                        if (!message.isNullOrBlank()) {
                            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
                launch {
                    viewModel.favoriteMessage.collect { event ->
                        val textRes = when (event) {
                            "added" -> R.string.add_to_favorites
                            else -> R.string.remove_from_favorites
                        }
                        Snackbar.make(binding.root, textRes, Snackbar.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.dashboardUi.collect { state ->
                        binding.progressBar.isVisible =
                            state is UiState.Loading && adapter.itemCount == 0

                        when (state) {
                            is UiState.Loading -> {
                                if (adapter.itemCount == 0) {
                                    binding.tvServersStatus.text = getString(R.string.loading)
                                }
                            }

                            is UiState.Success -> {
                                renderSuccess(state.data)
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

    private fun renderSuccess(data: DashboardUiState) {
        updateFavoritesChip(data.favoritesOnly)

        if (lastCriticalCount != data.criticalCount) {
            lastCriticalCount = data.criticalCount
            binding.tvCriticalCount.text = data.criticalCount.toString()
        }

        val serversText = if (data.serversTotal > 0) {
            "${data.serversOnline}/${data.serversTotal} online"
        } else {
            getString(R.string.servers_status_unknown)
        }
        val serversColor = ContextCompat.getColor(
            requireContext(),
            when {
                data.serversTotal == 0 -> R.color.text_secondary
                data.serversOnline < data.serversTotal -> R.color.status_warning
                else -> R.color.status_success
            }
        )
        if (lastServersStatusText != serversText) {
            lastServersStatusText = serversText
            binding.tvServersStatus.text = serversText
        }
        if (lastServersStatusColor != serversColor) {
            lastServersStatusColor = serversColor
            binding.tvServersStatus.setTextColor(serversColor)
        }

        val signature = objectsSignature(data.objects)
        if (signature != lastObjectsSignature || (adapter.itemCount == 0 && data.objects.isNotEmpty())) {
            lastObjectsSignature = signature
            adapter.submitList(data.objects.toList()) {
                binding.rvObjects.post { binding.rvObjects.requestLayout() }
            }
        }

        binding.tvEmptyObjects.isVisible = data.objects.isEmpty()
        binding.tvEmptyObjects.text = if (data.favoritesOnly) {
            getString(R.string.empty_favorites)
        } else {
            getString(R.string.empty_objects)
        }

        val titleText = if (data.favoritesCount > 0) {
            getString(R.string.favorites_section) +
                " · ${getString(R.string.monitoring_objects)}"
        } else {
            getString(R.string.monitoring_objects)
        }
        if (lastObjectsTitle != titleText) {
            lastObjectsTitle = titleText
            binding.tvObjectsTitle.text = titleText
        }
    }

    private fun updateFavoritesChip(checked: Boolean) {
        if (binding.chipFavoritesOnly.isChecked == checked) return
        binding.chipFavoritesOnly.setOnCheckedChangeListener(null)
        binding.chipFavoritesOnly.isChecked = checked
        binding.chipFavoritesOnly.setOnCheckedChangeListener(favoritesChipListener)
    }

    private fun objectsSignature(objects: List<MonitoringObject>): String =
        objects.joinToString("|") { item ->
            buildString {
                append(item.targetKey)
                append(';')
                append(item.isHealthy)
                append(';')
                append(item.openIncidents)
                append(';')
                append(item.worstSeverity)
                append(';')
                append(item.isFavorite)
                append(';')
                append(item.status)
                append(';')
                append(item.metricSummary)
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        lastObjectsSignature = null
        lastServersStatusText = null
        lastServersStatusColor = null
        lastCriticalCount = null
        lastObjectsTitle = null
        _binding = null
    }
}
