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
import com.example.monitoringapp.databinding.FragmentIncidentDetailBinding
import com.example.monitoringapp.domain.model.IncidentStatus
import com.example.monitoringapp.utils.UiState
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IncidentDetailFragment : Fragment() {

    private var _binding: FragmentIncidentDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: IncidentDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIncidentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val content = binding.incidentContent
        content.btnAccept.setOnClickListener { viewModel.accept() }
        content.btnConfirm.setOnClickListener { viewModel.complete() }
        content.btnClose.setOnClickListener { promptCloseFromFeatured() }
        content.btnGraphs.setOnClickListener { openGraphsForCurrentIncident() }
        content.chartMiniContainer.setOnClickListener { openGraphsForCurrentIncident() }
        binding.btnCloseIncident.setOnClickListener { promptCloseFromForm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.incident.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            is UiState.Success -> bindIncident(state.data)
                            is UiState.Error -> Snackbar.make(
                                binding.root, state.message, Snackbar.LENGTH_LONG
                            ).show()
                            else -> Unit
                        }
                    }
                }
                launch {
                    viewModel.chart.collect { chart ->
                        val state = viewModel.incident.value
                        if (state is UiState.Success && chart.points.isNotEmpty()) {
                            IncidentUiHelper.bindChart(
                                content,
                                state.data,
                                chart.points,
                                chart.threshold
                            )
                        }
                    }
                }
                launch {
                    viewModel.action.collect { state ->
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

    private fun bindIncident(incident: com.example.monitoringapp.domain.model.Incident) {
        val content = binding.incidentContent
        val chart = viewModel.chart.value
        IncidentUiHelper.bindTexts(content, incident, viewModel.currentUsername)
        IncidentUiHelper.bindChart(
            content,
            incident,
            chart.points,
            chart.threshold
        )
        val closed = incident.status == IncidentStatus.CLOSED
        binding.commentSection.isVisible = !closed && IncidentActionHelper
            .actionsFor(incident, viewModel.currentUsername).showClose
        binding.btnCloseIncident.isVisible = binding.commentSection.isVisible
    }

    private fun promptCloseFromFeatured() {
        val incident = (viewModel.incident.value as? UiState.Success)?.data ?: return
        IncidentCloseDialog.show(requireContext(), incident) { comment ->
            viewModel.close(comment)
        }
    }

    private fun promptCloseFromForm() {
        val incident = (viewModel.incident.value as? UiState.Success)?.data ?: return
        val inlineComment = binding.etComment.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (inlineComment != null) {
            viewModel.close(inlineComment)
            return
        }
        IncidentCloseDialog.show(requireContext(), incident) { comment ->
            viewModel.close(comment)
        }
    }

    private fun openGraphsForCurrentIncident() {
        val incident = (viewModel.incident.value as? UiState.Success)?.data ?: return
        val query = incident.metricName ?: return
        findNavController().navigate(
            com.example.monitoringapp.R.id.metricsFragment,
            bundleOf(
                "incidentId" to incident.id,
                "metricQuery" to query,
                "threshold" to (incident.threshold ?: 0.5f)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
