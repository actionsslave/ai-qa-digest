package app.aiqadigest

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Background refresh a few minutes after the curation job (08:00 Europe/Berlin).
 * A chain of one-time work requests: each run schedules the next slot before it fetches.
 * No exact alarms, no notifications; Android may delay runs for battery saving.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        schedule(applicationContext, replaceExisting = false)
        try {
            Repository(applicationContext).refresh()
        } catch (_: Exception) {
            // The failure is recorded by the repository and shown quietly in the app.
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "digest-update"
        private val ZONE = ZoneId.of("Europe/Berlin")
        private val SLOTS = listOf(LocalTime.of(8, 5))

        fun nextSlot(now: ZonedDateTime = ZonedDateTime.now(ZONE)): ZonedDateTime {
            val today = now.withZoneSameInstant(ZONE).toLocalDate()
            for (day in 0..1) for (t in SLOTS) {
                val c = today.plusDays(day.toLong()).atTime(t).atZone(ZONE)
                if (c.isAfter(now)) return c
            }
            error("unreachable")
        }

        /** Schedules the next slot. On app start existing requests are replaced; in the worker they are kept. */
        fun schedule(context: Context, replaceExisting: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (replaceExisting) wm.cancelAllWorkByTag(TAG)
            val slot = nextSlot()
            val delay = (slot.toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork("$TAG-${slot.toEpochSecond()}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
