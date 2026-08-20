package ua.rytm.app.ui.screens.finance

// SAMPLE DATA — for UI development/testing only, until the Room/Repository
// layer lands (ANDROID_MIGRATION.md §2, FINANCE_SCREEN_SPEC.md §7/§8). Not
// real user data, not persisted, not synced. Shapes match real PWA fields;
// the content itself (names/amounts) is illustrative.

object SampleFinanceData {
    val wallets = listOf(
        Wallet(id = "w1", name = "Готівка", colorHex = 0xFF8B5CF6, currency = "UAH"),
        Wallet(id = "w2", name = "Картка", colorHex = 0xFF3B82F6, currency = "UAH"),
        Wallet(id = "w3", name = "Заощадження", colorHex = 0xFF10B981, currency = "USD"),
    )

    val transactions = listOf(
        Transaction(id = "t1", type = TxType.EXPENSE, amount = 320.0, date = "2026-08-20", walletId = "w2", category = "Продукти", comment = "АТБ"),
        Transaction(id = "t2", type = TxType.INCOME, amount = 24000.0, date = "2026-08-19", walletId = "w2", category = "Зарплата"),
        Transaction(id = "t3", type = TxType.EXPENSE, amount = 89.0, date = "2026-08-19", walletId = "w1", category = "Транспорт", subcategory = "Таксі"),
        Transaction(
            id = "t4", type = TxType.TRANSFER, amount = 5000.0, date = "2026-08-18",
            walletId = "w2", targetWalletId = "w3", targetAmount = 135.0, targetCurrency = "USD",
            category = "Переказ",
        ),
        Transaction(id = "t5", type = TxType.EXPENSE, amount = 1200.0, date = "2026-08-17", walletId = "w2", category = "Комунальні"),
        Transaction(id = "t6", type = TxType.EXPENSE, amount = 450.0, date = "2026-08-15", walletId = "w1", category = "Кафе", comment = "З друзями", tags = listOf("дозвілля")),
        Transaction(id = "t7", type = TxType.EXPENSE, amount = 60.0, date = "2026-08-10", walletId = "w1", category = "Транспорт"),
    )

    // Sample category lists — the real PWA reads these per-account from
    // AppState.categories[type] (Firestore); this is illustrative content
    // matching the categories already used above, not a claim of parity
    // with any real account's actual category list.
    val incomeCategories = listOf("Зарплата", "Подарунок", "Інше")
    val expenseCategories = listOf("Продукти", "Транспорт", "Кафе", "Комунальні", "Здоров'я", "Розваги", "Інше")

    val subcategories: Map<String, List<String>> = mapOf(
        "Транспорт" to listOf("Таксі", "Автобус", "Паливо"),
        "Продукти" to listOf("Супермаркет", "Ринок"),
    )
}
