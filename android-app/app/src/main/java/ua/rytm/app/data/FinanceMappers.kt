package ua.rytm.app.data

import ua.rytm.app.data.local.RecurringEntity
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.data.local.WalletEntity
import ua.rytm.app.ui.screens.finance.Recurring
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet

fun WalletEntity.toDomain() = Wallet(id = id, name = name, colorHex = colorHex, currency = currency, icon = icon)
fun Wallet.toEntity() = WalletEntity(id = id, name = name, colorHex = colorHex, currency = currency, icon = icon)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    type = TxType.valueOf(type),
    amount = amount,
    currency = currency,
    date = date,
    walletId = walletId,
    targetWalletId = targetWalletId,
    targetAmount = targetAmount,
    targetCurrency = targetCurrency,
    category = category,
    subcategory = subcategory,
    comment = comment,
    tags = if (tags.isBlank()) emptyList() else tags.split(","),
)

fun Transaction.toEntity(createdAt: Long = System.currentTimeMillis()) = TransactionEntity(
    id = id,
    type = type.name,
    amount = amount,
    currency = currency,
    date = date,
    walletId = walletId,
    targetWalletId = targetWalletId,
    targetAmount = targetAmount,
    targetCurrency = targetCurrency,
    category = category,
    subcategory = subcategory,
    comment = comment,
    tags = tags.joinToString(","),
    createdAt = createdAt,
)

fun RecurringEntity.toDomain() = Recurring(
    id = id,
    type = TxType.valueOf(type),
    amount = amount,
    category = category,
    walletId = walletId,
    frequency = frequency,
    nextDate = nextDate,
    active = active,
    comment = comment,
)

fun Recurring.toEntity() = RecurringEntity(
    id = id,
    type = type.name,
    amount = amount,
    category = category,
    walletId = walletId,
    frequency = frequency,
    nextDate = nextDate,
    active = active,
    comment = comment,
)
