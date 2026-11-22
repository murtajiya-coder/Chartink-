package com.stocktools.dual

import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    private lateinit var sites: List<SiteController>

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // When connectivity returns, automatically retry any site that failed to load.
        override fun onAvailable(network: Network) {
            runOnUiThread { sites.filter { it.hasError }.forEach { it.retry() } }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sites = Site.entries.map { SiteController(this, it) }
        try {
            getSystemService(ConnectivityManager::class.java)
                .registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        setContent { AppTheme { App(sites) } }
    }

    override fun onResume() {
        super.onResume()
        sites.forEach { it.webView.onResume() }
    }

    override fun onPause() {
        sites.forEach { it.webView.onPause() }
        super.onPause()
    }

    override fun onDestroy() {
        try {
            getSystemService(ConnectivityManager::class.java)
                .unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        sites.forEach { it.destroy() }
        super.onDestroy()
    }
}
