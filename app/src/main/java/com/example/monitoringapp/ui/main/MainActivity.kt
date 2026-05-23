package com.example.monitoringapp.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.monitoringapp.R
import com.example.monitoringapp.databinding.ActivityMainBinding
import com.example.monitoringapp.domain.repository.AuthRepository
import com.example.monitoringapp.service.WebSocketForegroundService
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authRepository: AuthRepository

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && authRepository.receivesPushAlerts()) {
            WebSocketForegroundService.start(this)
        } else if (!granted && authRepository.receivesPushAlerts()) {
            Snackbar.make(
                binding.root,
                R.string.notification_permission_denied,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isLogin = destination.id == R.id.loginFragment
            binding.bottomNavigation.isVisible = !isLogin
            if (isLogin) {
                WebSocketForegroundService.stop(this)
            } else if (authRepository.isLoggedIn()) {
                startRealtimeIfEngineer()
            }
        }

        if (authRepository.isLoggedIn()) {
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.loginFragment, true)
                .build()
            navController.navigate(R.id.dashboardFragment, null, options)
            startRealtimeIfEngineer()
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (!authRepository.receivesPushAlerts()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            WebSocketForegroundService.start(this)
            return
        }
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                WebSocketForegroundService.start(this)
            }
            else -> requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startRealtimeIfEngineer() {
        if (!authRepository.receivesPushAlerts()) {
            WebSocketForegroundService.stop(this)
            return
        }
        requestNotificationPermissionIfNeeded()
    }
}
