package com.example.monitoringapp.ui.metrics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.monitoringapp.R
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

    private var rangeChipsFromCode = false
    private var suppressTextCallback = false
    private var suggestionsAdapter: ArrayAdapter<String>? = null
    private var lastSuggestions: List<String> = emptyList()

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
        setupMetricSearch()
        setupRangeChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.queryInput.collect { query ->
                        if (binding.actvMetricSearch.text?.toString() != query) {
                            suppressTextCallback = true
                            binding.actvMetricSearch.setText(query, false)
                            binding.actvMetricSearch.setSelection(query.length)
                            suppressTextCallback = false
                        }
                    }
                }
                launch {
                    viewModel.suggestions.collect { suggestions ->
                        if (suggestions == lastSuggestions) return@collect
                        lastSuggestions = suggestions
                        suggestionsAdapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            suggestions
                        )
                        binding.actvMetricSearch.setAdapter(suggestionsAdapter)
                        if (suggestions.isNotEmpty() && binding.actvMetricSearch.hasFocus()) {
                            binding.actvMetricSearch.showDropDown()
                        }
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
                                    keepExistingWhenEmpty = false
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

    private fun setupMetricSearch() {
        binding.tilMetricSearch.setEndIconOnClickListener {
            runMetricQuery()
        }
        binding.actvMetricSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollMetricsControls.post {
                    binding.scrollMetricsControls.smoothScrollTo(0, 0)
                }
            }
        }
        binding.actvMetricSearch.addTextChangedListener { text ->
            if (suppressTextCallback) return@addTextChangedListener
            viewModel.onQueryInputChanged(text?.toString().orEmpty())
        }
        binding.actvMetricSearch.setOnItemClickListener { _, _, position, _ ->
            val value = lastSuggestions.getOrNull(position) ?: return@setOnItemClickListener
            viewModel.selectSuggestion(value)
        }
        binding.actvMetricSearch.setOnEditorActionListener { _, actionId, _ ->
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
        viewModel.executeQuery(binding.actvMetricSearch.text?.toString())
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.actvMetricSearch.windowToken, 0)
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
