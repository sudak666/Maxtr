package ua.rytm.app.data

import kotlinx.coroutines.CancellationException

/**
 * runCatching for code that suspends: plain runCatching also catches
 * CancellationException, so a cancelled coroutine kept running past the
 * cancellation point (closed screen, signed-out account). This rethrows it.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
