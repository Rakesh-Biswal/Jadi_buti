package com.chefotech.jadibuti.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chefotech.jadibuti.scheduling.MaintenanceService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (sync.sync()) {
        is SyncOutcome.Success -> Result.success()
        is SyncOutcome.Offline -> if (runAttemptCount < 5) Result.retry() else Result.failure()
        is SyncOutcome.Failed -> if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val maintenance: MaintenanceService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        maintenance.runAll(recomputeZone = inputData.getBoolean(KEY_RECOMPUTE_ZONE, false))
        return Result.success()
    }

    companion object { const val KEY_RECOMPUTE_ZONE = "recomputeZone" }
}
