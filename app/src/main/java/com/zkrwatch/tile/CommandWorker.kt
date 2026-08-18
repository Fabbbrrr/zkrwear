package com.zkrwatch.tile

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zkrwatch.data.cache.StatusCache
import com.zkrwatch.presentation.ZkrViewModel

/**
 * Runs a Lock/Unlock command triggered by a Tile tap, off the UI path, then
 * refreshes the tile so it reflects the new lock state. This is what lets the
 * tile act without opening the app — the tile's clickable enqueues this worker
 * (see [ZkrTileService]).
 */
class CommandWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val repo = ZkrViewModel.buildRepository(applicationContext) ?: return Result.failure()
        return try {
            repo.connect()
            val vin = repo.firstVin()
            val ok = when (action) {
                ACTION_LOCK -> repo.lock(vin)
                ACTION_UNLOCK -> repo.unlock(vin)
                else -> return Result.failure()
            }
            // Reflect the car's new state in the cache the tile reads from.
            runCatching { StatusCache(applicationContext).write(vin, repo.status(vin)) }
            TileService.getUpdater(applicationContext).requestUpdate(ZkrTileService::class.java)
            if (ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val ACTION_LOCK = "lock"
        const val ACTION_UNLOCK = "unlock"
    }
}
