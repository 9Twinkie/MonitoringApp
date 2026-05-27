package com.example.monitoringapp.ui.metrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.FragmentMetricsBinding
import com.example.monitoringapp.domain.model.ChartTimeRange
import com.example.monitoringapp.domain.model.MetricPoint
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

    private var rangeChipsFromCode = false
    private var suppressTextCallback = false
    private lateinit var suggestionsAdapter: MetricSuggestionsAdapter

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
        binding.header.tvHeaderTitle.setText(R.string.graphs_title)
        ChartHelper.setupLineChart(binding.lineChart, requireContext(), interactive = true)

        suggestionsAdapter = MetricSuggestionsAdapter { suggestion ->
            hideKeyboard()
            binding.etMetricSearch.setText(suggestion)
            binding.etMetricSearch.setSelection(suggestion.length)
            viewModel.selectSuggestion(suggestion)
        }
        binding.rvMetricSuggestions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMetricSuggestions.adapter = suggestionsAdapter

        setupMetricSearch()
        setupRangeChips()
        renderChart(viewModel.chartPoints.value)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchFieldText.collect { text ->
                        if (binding.etMetricSearch.text?.toString() != text) {
                            suppressTextCallback = true
                            binding.etMetricSearch.setText(text)
                            binding.etMetricSearch.setSelection(text.length)
                            suppressTextCallback = false
                        }
                    }
                }
                launch {
                    viewModel.suggestions.collect { suggestions ->
                        suggestionsAdapter.submitList(suggestions)
                        binding.rvMetricSuggestions.isVisible = suggestions.isNotEmpty()
                    }
                }
                launch {
                    viewModel.searchHint.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.selectedRange.collect { range ->
                        syncRangeChipSelection(range)
                    }
                }
                launch {
                    viewModel.chartPoints.collect { state -> renderChart(state) }
                }
            }
        }
    }

    private fun renderChart(state: UiState<List<MetricPoint>>) {
        val chart = binding.lineChart
        binding.progressBar.isVisible = state is UiState.Loading
        when (state) {
            is UiState.Success -> chart.post {
                ChartHelper.bindMetricChart(
                    chart,
                    requireContext(),
                    primary = state.data,
                    threshold = viewModel.currentThreshold(),
                    keepExistingWhenEmpty = false
                )
            }
            is UiState.Error -> chart.post {
                ChartHelper.bindMetricChart(
                    chart,
                    requireContext(),
                    primary = emptyList(),
                    emptyText = state.message,
                    keepExistingWhenEmpty = false
                )
            }
            is UiState.Loading -> chart.post {
                ChartHelper.bindMetricChart(
                    chart,
                    requireContext(),
                    primary = emptyList(),
                    emptyText = getString(R.string.chart_loading),
                    keepExistingWhenEmpty = true
                )
            }
            else -> Unit
        }
    }

    private fun setupMetricSearch() {
        binding.tilMetricSearch.setEndIconOnClickListener {
            runMetricQuery()
        }
        binding.etMetricSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollMetricsControls.post {
                    binding.scrollMetricsControls.smoothScrollTo(0, 0)
                }
            }
        }
        binding.etMetricSearch.addTextChangedListener { text ->
            if (suppressTextCallback) return@addTextChangedListener
            viewModel.onQueryInputChanged(text?.toString().orEmpty())
        }
        binding.etMetricSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runMetricQuery()
                true
            } else {
                false
            }
        }
    }

    private fun runMetricQuery() {
        hideKeyboard()
        binding.rvMetricSuggestions.isVisible = false
        viewModel.executeQuery(binding.etMetricSearch.text?.toString().orEmpty())
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etMetricSearch.windowToken, 0)
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
