package com.chefotech.jadibuti

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.data.prefs.ThemeMode
import com.chefotech.jadibuti.ui.navigation.AppNavHost
import com.chefotech.jadibuti.ui.theme.JadiButiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsStore

    private var launchEventId by mutableStateOf<String?>(null)
    private var launchOpenStock by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            val prefs by settings.settings.collectAsState(initial = null)
            val dark = when (prefs?.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            JadiButiTheme(darkTheme = dark) {
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
