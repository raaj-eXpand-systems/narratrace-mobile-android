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
import io.narratrace.android.core.account.allowsOrdinaryAccess
import io.narratrace.android.core.auth.AuthState
import io.narratrace.android.core.network.ApiResult
import io.narratrace.android.core.runtime.RuntimeResolution
import java.util.concurrent.TimeUnit

class ProtectedUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = AppContainer(applicationContext)
        if (container.runtimeConfigRepository.resolve() != RuntimeResolution.Available) return Result.retry()
        val session = (container.sessionManager.restore() as? AuthState.Authenticated)?.session ?: return Result.success()
        val lifecycle = container.accountLifecycleApi.signal(session.accessToken)
        if (lifecycle !is ApiResult.Success || !lifecycle.value.allowsOrdinaryAccess()) return Result.success()
        val remaining = container.mediaRepository.reconcile()
        val issue = container.mediaRepository.latestReconciliationIssue()
        return if (remaining == 0 || issue?.retryAutomatically == false) Result.success() else Result.retry()
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
