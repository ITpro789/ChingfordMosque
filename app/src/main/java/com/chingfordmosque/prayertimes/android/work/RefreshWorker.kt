package com.chingfordmosque.prayertimes.android.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chingfordmosque.prayertimes.android.PrayerTimesApp
import com.chingfordmosque.prayertimes.domain.Result as CoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic background worker that keeps the cached schedule fresh and re-arms adhan alarms even
 * when the user never opens the app.
 *
 * It simply drives the platform-free [com.chingfordmosque.prayertimes.app.AppContainer.refreshNow]
 * off the IO dispatcher: on a successful fetch the cache, next-prayer state and notifications are
 * all updated by the coordinator; on a provider failure we ask WorkManager to retry later
 * (honouring its backoff policy) so a transient network blip does not leave the data stale.
 *
 * Enqueued as unique periodic work from [PrayerTimesApp.onCreate].
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        when (PrayerTimesApp.container().refreshNow()) {
            is CoreResult.Ok -> Result.success()
            is CoreResult.Err -> Result.retry()
        }
    }
}
