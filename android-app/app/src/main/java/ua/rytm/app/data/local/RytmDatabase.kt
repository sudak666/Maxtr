package ua.rytm.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WalletEntity::class, TransactionEntity::class, ShoppingItemEntity::class, CategoryEntity::class,
        ShiftTypeEntity::class, ShiftDayEntity::class, DebtEntity::class, DebtEntryEntity::class,
        SubcategoryEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class RytmDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun transactionDao(): TransactionDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shiftTypeDao(): ShiftTypeDao
    abstract fun shiftDayDao(): ShiftDayDao
    abstract fun debtDao(): DebtDao
    abstract fun debtEntryDao(): DebtEntryDao
    abstract fun subcategoryDao(): SubcategoryDao
}
