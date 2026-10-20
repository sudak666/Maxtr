package ua.rytm.app.data

/**
 * Category names that are data keys, not display text: they are stored in
 * Firestore exactly like this and shared with the PWA, so they must never be
 * translated in place. Display goes through LocalizedDomainText.
 */
object BuiltinCategories {
    const val TRANSFER = "Внутрішній переказ"
    const val OTHER = "Інше"
}
