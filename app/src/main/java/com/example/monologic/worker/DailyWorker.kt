package com.example.monologic.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// Full implementation in Task 9. Stub created here so WorkScheduler compiles.
class DailyWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
