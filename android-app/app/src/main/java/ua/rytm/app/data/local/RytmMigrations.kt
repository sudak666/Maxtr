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

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            fun scope(table: String, columns: String, definitions: String, keys: String) {
                db.execSQL("CREATE TABLE `${table}_v16` ($definitions, `ownerUid` TEXT NOT NULL, `profileId` TEXT NOT NULL, PRIMARY KEY($keys))")
                db.execSQL("INSERT INTO `${table}_v16` ($columns, `ownerUid`, `profileId`) SELECT $columns, '', 'default' FROM `$table`")
                db.execSQL("DROP TABLE `$table`")
                db.execSQL("ALTER TABLE `${table}_v16` RENAME TO `$table`")
            }
            scope("wallets", "`id`,`name`,`colorHex`,`currency`,`icon`", "`id` TEXT NOT NULL,`name` TEXT NOT NULL,`colorHex` INTEGER NOT NULL,`currency` TEXT NOT NULL,`icon` TEXT NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("transactions", "`id`,`type`,`amount`,`currency`,`date`,`walletId`,`targetWalletId`,`targetAmount`,`targetCurrency`,`category`,`subcategory`,`comment`,`tags`,`createdAt`,`monobankId`", "`id` TEXT NOT NULL,`type` TEXT NOT NULL,`amount` REAL NOT NULL,`currency` TEXT NOT NULL,`date` TEXT NOT NULL,`walletId` TEXT NOT NULL,`targetWalletId` TEXT,`targetAmount` REAL,`targetCurrency` TEXT,`category` TEXT NOT NULL,`subcategory` TEXT,`comment` TEXT,`tags` TEXT NOT NULL,`createdAt` INTEGER NOT NULL,`monobankId` TEXT", "`ownerUid`,`profileId`,`id`")
            db.execSQL("CREATE UNIQUE INDEX `index_transactions_ownerUid_profileId_monobankId` ON `transactions` (`ownerUid`,`profileId`,`monobankId`)")
            scope("shopping_items", "`id`,`name`,`qty`,`done`,`createdAt`", "`id` TEXT NOT NULL,`name` TEXT NOT NULL,`qty` INTEGER NOT NULL,`done` INTEGER NOT NULL,`createdAt` INTEGER NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("categories", "`id`,`type`,`name`", "`id` TEXT NOT NULL,`type` TEXT NOT NULL,`name` TEXT NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("shift_types", "`id`,`name`,`short`,`code`,`colorHex`,`amount`,`hours`,`isOff`", "`id` TEXT NOT NULL,`name` TEXT NOT NULL,`short` TEXT NOT NULL,`code` TEXT NOT NULL,`colorHex` INTEGER NOT NULL,`amount` REAL NOT NULL,`hours` REAL NOT NULL,`isOff` INTEGER NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("shift_days", "`dateKey`,`shiftTypeId`", "`dateKey` TEXT NOT NULL,`shiftTypeId` TEXT NOT NULL", "`ownerUid`,`profileId`,`dateKey`,`shiftTypeId`")
            scope("debts", "`id`,`name`,`note`,`currency`,`startAmount`,`dueDate`", "`id` INTEGER NOT NULL,`name` TEXT NOT NULL,`note` TEXT NOT NULL,`currency` TEXT NOT NULL,`startAmount` REAL NOT NULL,`dueDate` TEXT NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("debt_entries", "`id`,`debtId`,`amount`,`balance`,`date`", "`id` INTEGER NOT NULL,`debtId` INTEGER NOT NULL,`amount` TEXT NOT NULL,`balance` REAL NOT NULL,`date` TEXT NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("subcategories", "`categoryType`,`categoryName`,`name`", "`categoryType` TEXT NOT NULL,`categoryName` TEXT NOT NULL,`name` TEXT NOT NULL", "`ownerUid`,`profileId`,`categoryType`,`categoryName`,`name`")
            scope("budgets", "`category`,`amount`", "`category` TEXT NOT NULL,`amount` REAL NOT NULL", "`ownerUid`,`profileId`,`category`")
            scope("tags", "`id`,`name`,`colorHex`", "`id` TEXT NOT NULL,`name` TEXT NOT NULL,`colorHex` INTEGER NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("recurring", "`id`,`type`,`amount`,`category`,`walletId`,`frequency`,`nextDate`,`active`,`comment`", "`id` TEXT NOT NULL,`type` TEXT NOT NULL,`amount` REAL NOT NULL,`category` TEXT NOT NULL,`walletId` TEXT NOT NULL,`frequency` TEXT NOT NULL,`nextDate` TEXT NOT NULL,`active` INTEGER NOT NULL,`comment` TEXT NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("category_icons", "`categoryName`,`iconName`", "`categoryName` TEXT NOT NULL,`iconName` TEXT NOT NULL", "`ownerUid`,`profileId`,`categoryName`")
            scope("goals", "`id`,`walletId`,`targetAmount`,`targetDate`", "`id` TEXT NOT NULL,`walletId` TEXT NOT NULL,`targetAmount` REAL NOT NULL,`targetDate` TEXT NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("currency_rates", "`code`,`rateToUah`", "`code` TEXT NOT NULL,`rateToUah` REAL NOT NULL", "`ownerUid`,`profileId`,`code`")
            scope("autofill_schedule", "`id`,`enabled`,`typeId`,`pattern`,`anchorDate`", "`id` INTEGER NOT NULL,`enabled` INTEGER NOT NULL,`typeId` TEXT NOT NULL,`pattern` TEXT NOT NULL,`anchorDate` TEXT NOT NULL", "`ownerUid`,`profileId`,`id`")
            scope("auto_rules", "`id`,`type`,`keyword`,`category`,`position`", "`id` TEXT NOT NULL,`type` TEXT NOT NULL,`keyword` TEXT NOT NULL,`category` TEXT NOT NULL,`position` INTEGER NOT NULL", "`ownerUid`,`profileId`,`id`")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`operationId` TEXT NOT NULL, `ownerUid` TEXT NOT NULL, `profileId` TEXT NOT NULL, `domain` TEXT NOT NULL, `entityId` TEXT NOT NULL, `operation` TEXT NOT NULL, `payload` TEXT, `createdAt` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, `lastErrorCode` TEXT, PRIMARY KEY(`operationId`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_ownerUid_profileId_domain_entityId` ON `sync_outbox` (`ownerUid`, `profileId`, `domain`, `entityId`)")
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL = arrayOf(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
}
