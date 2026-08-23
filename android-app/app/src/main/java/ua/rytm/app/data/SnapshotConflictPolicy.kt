package ua.rytm.app.data

internal class SnapshotConflictException : RuntimeException()

internal fun Throwable.snapshotConflict(): Boolean =
    generateSequence(this as Throwable?) { it.cause }.take(8).any { it is SnapshotConflictException }
