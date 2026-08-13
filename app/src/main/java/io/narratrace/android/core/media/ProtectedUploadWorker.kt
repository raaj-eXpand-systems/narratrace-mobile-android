package io.narratrace.android.core.media

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.narratrace.android.app.AppContainer
import java.util.concurrent.TimeUnit

class ProtectedUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        container.sessionManager.restore()
        val remaining = container.mediaRepository.reconcile()
        return if (remaining == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "protected-media-reconciliation"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProtectedUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
            )
        }
    }
}
