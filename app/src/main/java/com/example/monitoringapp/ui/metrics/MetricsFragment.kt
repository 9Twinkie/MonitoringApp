package com.example.monitoringapp.ui.metrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.monitoringapp.databinding.FragmentMetricsBinding
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.utils.ChartHelper
import com.example.monitoringapp.utils.UiState
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MetricsFragment : Fragment() {

    private var _binding: FragmentMetricsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MetricsViewModel by viewModels()

    private var spinnerFromCode = false
    private var lastSpinnerLabels: List<String> = emptyList()
    private var rangeChipsFromCode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMetricsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ChartHelper.setupLineChart(binding.lineChart, requireContext(), interactive = true)
        setupRangeChips()

        binding.spinnerMetrics.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spinnerFromCode) return
                viewModel.selectSeries(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onScreenVisible(readNavArgs())
                launch {
                    viewModel.options.collect { options ->
                        val labels = options.map { it.label }
                        if (labels == lastSpinnerLabels) return@collect
                        lastSpinnerLabels = labels
                        spinnerFromCode = true
                        binding.spinnerMetrics.adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_dropdown_item,
                            labels
                        )
                        val index = viewModel.selectedIndex.value.coerceIn(0, labels.lastIndex.coerceAtLeast(0))
                        if (index in labels.indices) {
                            binding.spinnerMetrics.setSelection(index, false)
                        }
                        spinnerFromCode = false
                    }
                }
                launch {
                    viewModel.selectedRange.collect { range ->
                        syncRangeChipSelection(range)
                    }
                }
                launch {
                    viewModel.chartPoints.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            is UiState.Success -> {
                                ChartHelper.bindMetricChart(
                                    binding.lineChart,
                                    requireContext(),
                                    primary = state.data,
                                    threshold = viewModel.currentThreshold(),
                                    keepExistingWhenEmpty = false
                                )
                            }
                            is UiState.Error -> {
                                ChartHelper.bindMetricChart(
                                    binding.lineChart,
                                    requireContext(),
                                    primary = emptyList(),
                                    emptyText = state.message,
                                    keepExistingWhenEmpty = true
                                )
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun setupRangeChips() {
        binding.chipGroupRange.setOnCheckedStateChangeListener { group, checkedIds ->
            if (rangeChipsFromCode || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedIds.first()) ?: return@setOnCheckedStateChangeListener
            val range = chip.tag as? ChartTimeRange ?: return@setOnCheckedStateChangeListener
            viewModel.selectRange(range)
        }

        rangeChipsFromCode = true
        binding.chipGroupRange.removeAllViews()
        ChartTimeRange.entries.forEach { range ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(range.labelRes)
                isCheckable = true
                tag = range
            }
            binding.chipGroupRange.addView(chip)
        }
        rangeChipsFromCode = false
        syncRangeChipSelection(ChartTimeRange.HOUR)
    }

    private fun syncRangeChipSelection(range: ChartTimeRange) {
        rangeChipsFromCode = true
        for (i in 0 until binding.chipGroupRange.childCount) {
            val chip = binding.chipGroupRange.getChildAt(i) as? Chip ?: continue
            if (chip.tag == range) {
                binding.chipGroupRange.check(chip.id)
                break
            }
        }
        rangeChipsFromCode = false
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenVisible(readNavArgs())
    }

    private fun readNavArgs(): MetricsNavArgs {
        val args = findNavController().currentBackStackEntry?.arguments
        return MetricsNavArgs(
            incidentId = args?.getLong("incidentId", -1L) ?: -1L,
            metricQuery = args?.getString("metricQuery"),
            threshold = args?.getFloat("threshold", -1f) ?: -1f
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
