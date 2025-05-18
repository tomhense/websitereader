package com.example.websitereader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.websitereader.databinding.ActivityMainBinding
import com.example.websitereader.settings.SettingsActivity

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Save original toolbar top padding
        val originalToolbarPaddingTop = binding.topAppBar.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Add status bar height to toolbar paddingTop
            binding.topAppBar.updatePadding(top = originalToolbarPaddingTop + sysBars.top)

            // FAB: adjust bottom and end margins
            (binding.settingsButton.layoutParams as CoordinatorLayout.LayoutParams).apply {
                val baseMargin = 16.dp
                marginEnd = baseMargin + sysBars.right
                bottomMargin = baseMargin + sysBars.bottom
                binding.settingsButton.layoutParams = this
            }
            WindowInsetsCompat.CONSUMED
        }

        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    true
                }

                else -> false
            }
        }
    }

    // Extension to convert dp to px
    val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
