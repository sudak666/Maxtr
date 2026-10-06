package ua.rytm.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Every Room migration from v13 on. RytmMigrationsTest checks the chain reaches the current version. */
object RytmMigrations {
    const val FIRST_MIGRATED_VERSION = 13

    val ALL: Array<Migration> = arrayOf(
        object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `auto_rules` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `keyword` TEXT NOT NULL, `category` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        },
        object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `monobankId` TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_monobankId` ON `transactions` (`monobankId`)")
            }
        },
        object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Shopping list was removed; its cache table goes with it.
                db.execSQL("DROP TABLE IF EXISTS `shopping_items`")
            }
        },
        object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `createdBy` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `createdByName` TEXT")
            }
        },
    )
}
