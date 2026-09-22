package com.chefotech.jadibuti

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.chefotech.jadibuti.data.prefs.SettingsStore
import com.chefotech.jadibuti.reminders.ReminderNotifier
import com.chefotech.jadibuti.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class JadiButiApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var syncScheduler: SyncScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).setMinimumLoggingLevel(android.util.Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        scope.launch { notifier.ensureChannels(settings.current()) }
        syncScheduler.schedulePeriodic()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Every time the app comes to the foreground: refresh the window, alarms and cloud data.
                syncScheduler.requestMaintenance()
                syncScheduler.requestSync()
            }
        })
    }
}
