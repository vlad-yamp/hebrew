package com.example.hebrew

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.hebrew.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val hideBottomNav = destination.id == R.id.translationFragment ||
                    destination.id == R.id.learningFragment
            binding.bottomNavigation.visibility =
                if (hideBottomNav) View.GONE else View.VISIBLE

            sendFloatingAction(
                if (destination.id == R.id.voiceFragment) FloatingMicService.ACTION_HIDE
                else FloatingMicService.ACTION_SHOW
            )
        }

        requestOverlayPermissionOnFirstLaunch()
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingMicService::class.java))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == FloatingMicService.ACTION_OPEN_VOICE) {
            navController.popBackStack(R.id.voiceFragment, false)
        }
    }

    private fun requestOverlayPermissionOnFirstLaunch() {
        if (Settings.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("overlay_asked", false)) return
        prefs.edit().putBoolean("overlay_asked", true).apply()
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun sendFloatingAction(action: String) {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingMicService::class.java).apply { this.action = action })
        }
    }
}
