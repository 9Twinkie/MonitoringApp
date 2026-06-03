package com.example.monitoringapp.ui.settings

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
    private var switchFromCode = false

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
        binding.header.tvHeaderTitle.setText(R.string.profile_title)
        viewModel.refreshProfile()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.username.collect { name ->
                        binding.tvUsername.text = name ?: "—"
                    }
                }
                launch {
                    viewModel.userRole.collect { role ->
                        binding.tvRole.text = roleLabel(role)
                    }
                }
                launch {
                    viewModel.canManageUsers.collect { canManage ->
                        binding.btnManageUsers.isVisible = canManage
                    }
                }
                launch {
                    viewModel.baseUrl.collect { url ->
                        binding.etServerUrl.setText(url)
                    }
                }
                launch {
                    viewModel.notifyFavoritesOnly.collect { enabled ->
                        switchFromCode = true
                        binding.switchNotifyFavorites.isChecked = enabled
                        switchFromCode = false
                    }
                }
            }
        }

        binding.btnManageUsers.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_users)
        }

        binding.switchNotifyFavorites.setOnCheckedChangeListener { _, checked ->
            if (!switchFromCode) {
                viewModel.setNotifyFavoritesOnly(checked)
            }
        }

        binding.btnSave.setOnClickListener {
            val url = binding.etServerUrl.text?.toString().orEmpty()
            if (url.isNotBlank()) {
                val sessionReset = viewModel.saveBaseUrl(url)
                val message = if (sessionReset) {
                    R.string.server_url_changed_relogin
                } else {
                    R.string.btn_save
                }
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                if (sessionReset) {
                    WebSocketForegroundService.stop(requireContext())
                    val options = NavOptions.Builder()
                        .setPopUpTo(R.id.mobile_navigation, true)
                        .build()
                    findNavController().navigate(R.id.loginFragment, null, options)
                }
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

    override fun onResume() {
        super.onResume()
        viewModel.refreshProfile()
        binding.etServerUrl.setText(viewModel.currentBaseUrl())
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
