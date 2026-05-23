package com.example.monitoringapp.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.FragmentSettingsBinding
import com.example.monitoringapp.domain.model.UserRole
import com.example.monitoringapp.service.WebSocketForegroundService
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvUsername.text = viewModel.username() ?: "—"
        binding.tvRole.text = roleLabel(viewModel.userRole())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.baseUrl.collect { url ->
                    if (binding.etServerUrl.text.isNullOrBlank()) {
                        binding.etServerUrl.setText(url)
                    }
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val url = binding.etServerUrl.text?.toString().orEmpty()
            if (url.isNotBlank()) {
                viewModel.saveBaseUrl(url)
                Snackbar.make(binding.root, R.string.btn_save, Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnLogout.setOnClickListener {
            WebSocketForegroundService.stop(requireContext())
            viewModel.logout()
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.mobile_navigation, true)
                .build()
            findNavController().navigate(R.id.loginFragment, null, options)
        }
    }

    private fun roleLabel(role: UserRole): String = when (role) {
        UserRole.ENGINEER -> getString(R.string.role_engineer)
        UserRole.ADMIN -> getString(R.string.role_admin)
        UserRole.OTHER -> getString(R.string.role_other)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
