package ua.rytm.app.data.local

import androidx.room.Entity
import androidx.room.Index

// Room-persisted finance models. Transaction tag ids use a comma-separated
// field while tag metadata lives in its dedicated table.

@Entity(tableName = "wallets", primaryKeys = ["ownerUid", "profileId", "id"])
data class WalletEntity(
    val id: String,
    val name: String,
    val colorHex: Long,
    val currency: String,
    val icon: String = "card",
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
)

@Entity(tableName = "transactions", primaryKeys = ["ownerUid", "profileId", "id"], indices = [Index(value = ["ownerUid", "profileId", "monobankId"], unique = true)])
data class TransactionEntity(
    val id: String,
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
    val monobankId: String? = null,
    val ownerUid: String = RoomProfileScope.ownerUid,
    val profileId: String = RoomProfileScope.profileId,
    val revision: Long = 0,
    val updatedAt: Long = createdAt,
)
