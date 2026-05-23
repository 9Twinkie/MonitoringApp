package com.example.monitoringapp.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.monitoringapp.domain.repository.IncidentRepository
import com.example.monitoringapp.utils.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val incidentRepository: IncidentRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return incidentRepository.syncPendingActions()
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val NAME = Constants.WORK_SYNC_PENDING
    }
}
