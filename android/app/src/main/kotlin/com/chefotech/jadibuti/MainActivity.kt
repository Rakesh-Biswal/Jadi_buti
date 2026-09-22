package com.chefotech.jadibuti

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chefotech.jadibuti.ui.navigation.AppNavHost
import com.chefotech.jadibuti.ui.theme.JadiButiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var launchEventId by mutableStateOf<String?>(null)
    private var launchOpenStock by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            JadiButiTheme {
                AppNavHost(launchEventId = launchEventId, openStock = launchOpenStock, onLaunchHandled = { launchEventId = null; launchOpenStock = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        launchEventId = intent?.getStringExtra(EXTRA_EVENT_ID)
        launchOpenStock = intent?.getBooleanExtra(EXTRA_OPEN_STOCK, false) == true
    }

    companion object {
        const val EXTRA_EVENT_ID = "openEventId"
        const val EXTRA_OPEN_STOCK = "openStock"
    }
}
