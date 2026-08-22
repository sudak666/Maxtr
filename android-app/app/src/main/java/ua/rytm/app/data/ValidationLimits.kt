package ua.rytm.app.data

import kotlin.math.abs

const val AMOUNT_MAX = 1_000_000_000.0

fun isStoredAmountValid(value: Double): Boolean = value.isFinite() && abs(value) < AMOUNT_MAX

fun requireValidStoredAmount(value: Double, field: String = "amount") {
    require(isStoredAmountValid(value)) { "$field must be finite and have absolute value below $AMOUNT_MAX" }
}
