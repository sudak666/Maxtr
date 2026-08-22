package ua.rytm.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RytmMigrations {
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `auto_rules` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `keyword` TEXT NOT NULL, `category` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `monobankId` TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_monobankId` ON `transactions` (`monobankId`)")
        }
    }

    val ALL = arrayOf(MIGRATION_13_14, MIGRATION_14_15)
}
