package com.chefotech.jadibuti.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Enqueues WorkManager jobs. Cheap to call often; WorkManager de-duplicates by name. */
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sync as soon as the network is available (runs immediately when online). */
    fun requestSync() {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun requestMaintenance(recomputeZone: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<MaintenanceWorker>()
            .setInputData(androidx.work.workDataOf(MaintenanceWorker.KEY_RECOMPUTE_ZONE to recomputeZone))
            .build()
        wm.enqueueUniqueWork(MAINTENANCE_NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun schedulePeriodic() {
        wm.enqueueUniquePeriodicWork(
            SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(online).build(),
        )
        wm.enqueueUniquePeriodicWork(
            MAINTENANCE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(15, TimeUnit.MINUTES).build(),
        )
    }

    private companion object {
        const val SYNC_NOW = "sync-now"
        const val SYNC_PERIODIC = "sync-periodic"
        const val MAINTENANCE_NOW = "maintenance-now"
        const val MAINTENANCE_PERIODIC = "maintenance-periodic"
    }
}
