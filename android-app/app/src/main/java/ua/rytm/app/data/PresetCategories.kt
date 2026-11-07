package ua.rytm.app.data

/**
 * The ready-made category set a new profile starts with (owner decision
 * 2026-11-03, after comparing with a competitor whose newcomer has nothing to
 * set up). Stored on the wire by its Ukrainian name like every category; the
 * English names are presentation only ([englishName]).
 *
 * Names already used by older seeds (Продукти, Кафе, Транспорт, Комунальні,
 * Здоров'я, Розваги, Покупки, Зарплата, Підробіток, Інше) are kept as they
 * were, so existing accounts merging the set get subcategories under their
 * own categories instead of near-duplicates.
 */
object PresetCategories {
    data class Preset(val name: String, val english: String, val subcategories: List<Pair<String, String>> = emptyList())

    val expense: List<Preset> = listOf(
        Preset("Продукти", "Groceries", listOf("Супермаркет" to "Supermarket", "Ринок" to "Market", "Алкоголь" to "Alcohol")),
        Preset("Кафе", "Cafe", listOf("Кава і перекус" to "Coffee and snacks", "Ресторани" to "Restaurants", "Доставка їжі" to "Food delivery")),
        Preset("Транспорт", "Transport", listOf("Пальне" to "Fuel", "Таксі" to "Taxi", "Громадський транспорт" to "Public transport", "Паркування" to "Parking", "Обслуговування авто" to "Car service")),
        Preset("Комунальні", "Utilities", listOf("Оренда" to "Rent", "Комунальні послуги" to "Utility bills", "Інтернет і ТБ" to "Internet and TV")),
        Preset("Зв'язок і підписки", "Phone and subscriptions", listOf("Мобільний зв'язок" to "Mobile", "Підписки" to "Subscriptions")),
        Preset("Покупки", "Shopping", listOf("Одяг і взуття" to "Clothes and shoes", "Техніка" to "Electronics", "Подарунки" to "Gifts")),
        Preset("Дім і побут", "Home", listOf("Меблі" to "Furniture", "Побутова хімія" to "Household supplies", "Ремонт" to "Repairs")),
        Preset("Здоров'я", "Health", listOf("Аптека" to "Pharmacy", "Лікарі" to "Doctors", "Краса" to "Beauty")),
        Preset("Розваги", "Entertainment", listOf("Кіно і події" to "Cinema and events", "Ігри" to "Games", "Хобі" to "Hobbies")),
        Preset("Спорт", "Sport", listOf("Абонемент" to "Membership", "Спорядження" to "Gear")),
        Preset("Освіта", "Education", listOf("Курси" to "Courses", "Книги" to "Books")),
        Preset("Діти", "Kids", listOf("Садок і школа" to "Kindergarten and school", "Іграшки" to "Toys", "Дитячий одяг" to "Kids' clothes")),
        Preset("Тварини", "Pets", listOf("Корм" to "Food", "Ветеринар" to "Vet")),
        Preset("Подорожі", "Travel", listOf("Житло в поїздці" to "Accommodation", "Квитки" to "Tickets")),
        Preset("Благодійність", "Charity", listOf("Донати" to "Donations", "Волонтерство" to "Volunteering")),
        Preset("Обов'язкові платежі", "Obligatory payments", listOf("Податки" to "Taxes", "Кредити" to "Loans", "Страхування" to "Insurance")),
        Preset(BuiltinCategories.OTHER, "Other"),
    )

    val income: List<Preset> = listOf(
        Preset("Зарплата", "Salary", listOf("Основна" to "Main", "Аванс" to "Advance", "Премія" to "Bonus")),
        Preset("Підробіток", "Side job", listOf("Фріланс" to "Freelance", "Разові підробітки" to "One-off jobs")),
        Preset("Бізнес і ФОП", "Business", listOf("Дохід від бізнесу" to "Business income", "ФОП" to "Sole proprietor")),
        Preset("Пасивний дохід", "Passive income", listOf("Депозит" to "Deposit", "Кешбек" to "Cashback", "Оренда" to "Rent")),
        Preset("Подарунки і допомога", "Gifts and support", listOf("Подарунки" to "Gifts", "Допомога від рідних" to "Help from family", "Соцвиплати" to "Social payments")),
        Preset(BuiltinCategories.OTHER, "Other"),
    )

    fun of(type: String): List<Preset> = if (type == "INCOME") income else expense

    /**
     * [categories] with the ready-made ones first, in the set's own order (most
     * common first), the rest after. A new profile has no history, and the
     * alphabetical first pick was «Благодійність» (first-run audit 2026-11-07).
     */
    fun starterOrder(type: String, categories: List<String>): List<String> {
        val rank = of(type).withIndex().associate { (i, p) -> p.name to i }
        return categories.sortedBy { rank[it] ?: Int.MAX_VALUE }
    }

    private val english: Map<String, String> by lazy {
        (expense + income).flatMap { p -> listOf(p.name to p.english) + p.subcategories }.toMap()
    }

    /** English name of a preset category or subcategory; null for anything the user typed. */
    fun englishName(name: String): String? = english[name]
}
