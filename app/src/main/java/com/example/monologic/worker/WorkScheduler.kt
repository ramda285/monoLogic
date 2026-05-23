package com.example.monologic.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val WORK_NAME = "daily_topic_work"

    /**
     * 1時間後に「未リプライ → 再通知」チェックをスケジュールする。
     * ExistingWorkPolicy.KEEP で重複防止。
     */
    fun scheduleReplyCheck1h(context: Context, date: String) {
        val data = workDataOf(
            ReplyCheckWorker.KEY_DATE       to date,
            ReplyCheckWorker.KEY_CHECK_TYPE to ReplyCheckWorker.CHECK_1H
        )
        val work = OneTimeWorkRequestBuilder<ReplyCheckWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("replyCheck1h_$date", ExistingWorkPolicy.KEEP, work)
    }

    /**
     * 3時間後に「タイムアウト / キーワード抽出」チェックをスケジュールする。
     */
    fun scheduleReplyCheck3h(context: Context, date: String) {
        val data = workDataOf(
            ReplyCheckWorker.KEY_DATE       to date,
            ReplyCheckWorker.KEY_CHECK_TYPE to ReplyCheckWorker.CHECK_3H
        )
        val work = OneTimeWorkRequestBuilder<ReplyCheckWorker>()
            .setInitialDelay(3, TimeUnit.HOURS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("replyCheck3h_$date", ExistingWorkPolicy.KEEP, work)
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val delay = computeDelayMillis(hour, minute)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DailyWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE, request
        )
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

    /** 次の hour:minute までのミリ秒を返す。すでに過ぎていれば翌日分を返す。常に正の値。 */
    fun computeDelayMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }
}
