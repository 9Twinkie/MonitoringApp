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
import com.example.monitoringapp.databinding.FragmentIncidentDetailBinding
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
        content.btnConfirm.setOnClickListener { viewModel.confirm() }
        content.btnGraphs.setOnClickListener {
            val incident = (viewModel.incident.value as? UiState.Success)?.data ?: return@setOnClickListener
            val query = incident.metricName ?: return@setOnClickListener
            findNavController().navigate(
                com.example.monitoringapp.R.id.metricsFragment,
                bundleOf(
                    "incidentId" to incident.id,
                    "metricQuery" to query,
                    "threshold" to (incident.threshold ?: 0.5f)
                )
            )
        }
        binding.btnCloseIncident.setOnClickListener { viewModel.close() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.incident.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            is UiState.Success -> {
                                val chart = viewModel.chart.value
                                IncidentUiHelper.bindTexts(content, state.data)
                                IncidentUiHelper.bindChart(
                                    content,
                                    state.data,
                                    chart.points,
                                    chart.threshold
                                )
                            }
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
                        if (state is UiState.Success) {
                            Snackbar.make(binding.root, "OK", Snackbar.LENGTH_SHORT).show()
                        } else if (state is UiState.Error) {
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
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
