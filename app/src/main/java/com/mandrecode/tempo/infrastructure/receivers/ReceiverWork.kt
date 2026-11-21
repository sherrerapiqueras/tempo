package com.mandrecode.tempo.infrastructure.receivers

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs [block] off the main thread for the rest of this broadcast, the way every receiver that
 * touches the database or a use case has to (`goAsync()` plus a coroutine, finished in `finally`).
 *
 * The scope has no parent and no exception handler, so before this helper an exception thrown by
 * the work — the database failing to open, a full disk, a bug in a use case — reached the thread's
 * default handler and killed the process from the background: a notification action or an alarm
 * turned into a crash the user never asked for. Here it is logged and dropped instead. The next
 * alarm, the next app start or the periodic refresh gets another go at the same work.
 */
internal fun BroadcastReceiver.launchReceiverWork(
    dispatcher: CoroutineDispatcher,
    tag: String,
    block: suspend CoroutineScope.() -> Unit,
) {
    val pendingResult = goAsync()
    CoroutineScope(dispatcher).launch {
        runReceiverWork(
            block = { block() },
            onError = { error -> Log.e(tag, "Background work for a broadcast failed", error) },
            finish = { pendingResult.finish() },
        )
    }
}

/**
 * The part of [launchReceiverWork] that decides what happens to a failure, apart from the Android
 * types so it can be tested: [block] runs, a failure other than cancellation goes to [onError], and
 * [finish] runs whatever happened.
 */
@Suppress("TooGenericExceptionCaught") // Any failure: the point is that none of them may crash the app.
internal suspend fun runReceiverWork(
    block: suspend () -> Unit,
    onError: (Throwable) -> Unit,
    finish: () -> Unit,
) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    } finally {
        finish()
    }
}
