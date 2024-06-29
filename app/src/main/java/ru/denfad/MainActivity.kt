package ru.denfad.bluetoothwriterreader

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import ru.denfad.bluetoothwriterreader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity"
    private val connectionViewModel: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.bottomNavigationView

        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    Log.d(TAG, "Home selected")
                    loadFragment(HomeFragment(), "HOME")
                    return@setOnItemSelectedListener true
                }
                R.id.navigation_connection -> {
                    Log.d(TAG, "Connection selected")
                    loadFragment(ConnectionFragment(), "CONNECTION")
                    return@setOnItemSelectedListener true
                }
            }
            false
        }

        if (savedInstanceState == null) {
            navView.selectedItemId = R.id.navigation_home
            Log.d(TAG, "Setting default fragment to Home")
        }

    }

    private fun loadFragment(fragment: Fragment, tag: String) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        val currentFragment = fragmentManager.findFragmentByTag(tag)

        if (currentFragment == null || !currentFragment.isVisible) {
            Log.d(TAG, "Loading fragment: ${fragment::class.java.simpleName}")
            fragmentTransaction.replace(R.id.nav_host_fragment, fragment, tag)
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            fragmentTransaction.commitAllowingStateLoss()
        }
    }
}
