package ua.rytm.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Room-persisted shape of ua.rytm.app.ui.screens.finance's domain models
// (ANDROID_MIGRATION.md §2.1). `tags` is a comma-joined string, not a
// separate table — an honest simplification until a real Tag{id,name,color}
// entity is ported (see FINANCE_SCREEN_SPEC.md §9's "not in Step 3" note).

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val currency: String,
    val icon: String = "card",
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String, // TxType.name
    val amount: Double,
    val currency: String,
    val date: String, // "yyyy-MM-dd"
    val walletId: String,
    val targetWalletId: String?,
    val targetAmount: Double?,
    val targetCurrency: String?,
    val category: String,
    val subcategory: String?,
    val comment: String?,
    val tags: String, // comma-joined; "" when empty
    val createdAt: Long,
)
