package com.example.websitereader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.websitereader.databinding.ActivityMainBinding
import com.example.websitereader.settings.SettingsActivity

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
}
