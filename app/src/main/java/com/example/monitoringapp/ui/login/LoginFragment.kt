package com.example.monitoringapp.ui.login

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
import androidx.navigation.fragment.findNavController
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.FragmentLoginBinding
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.ui.main.MainActivity
import com.google.android.material.snackbar.Snackbar
import com.example.monitoringapp.utils.UiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    @Inject lateinit var authRepository: AuthRepository

    private var lastShownServerUrl: String? = null

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLogin.setOnClickListener {
            viewModel.login(
                binding.etUsername.text?.toString().orEmpty(),
                binding.etPassword.text?.toString().orEmpty(),
                binding.etServerUrl.text?.toString().orEmpty()
            )
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.serverUrl.collect { url ->
                        val field = binding.etServerUrl.text?.toString().orEmpty()
                        if (field.isBlank() || field == lastShownServerUrl) {
                            binding.etServerUrl.setText(url)
                            lastShownServerUrl = url
                        }
                    }
                }
                launch {
                    viewModel.state.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        binding.btnLogin.isEnabled = state !is UiState.Loading
                        when (state) {
                            is UiState.Success -> {
                                findNavController().navigate(R.id.action_login_to_main)
                                val activity = requireActivity() as MainActivity
                                if (authRepository.receivesPushAlerts()) {
                                    activity.requestNotificationPermissionIfNeeded()
                                }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
