package ua.rytm.app.bank

import ua.rytm.app.ui.screens.finance.Wallet

/**
 * Which wallet a bank push belongs to: card digits in the wallet's name win
 * ("ПУМБ 5536"), then the bank's name words ("monobank"), then [currency].
 * Shared by the pre-filled form and the save-from-notification actions.
 */
object WalletMatcher {
    fun forCard(wallets: List<Wallet>, mask: String?, bankPackage: String?): Wallet? {
        val digits = mask?.filter { it.isDigit() }?.takeIf { it.length >= 2 }
        digits?.let { d -> wallets.firstOrNull { it.name.contains(d) }?.let { return it } }
        val words = BankNotificationParser.bankWords(bankPackage)
        return wallets.firstOrNull { w -> words.any { w.name.contains(it, ignoreCase = true) } }
    }

    fun pick(wallets: List<Wallet>, mask: String?, bankPackage: String?, currency: String?): Wallet? =
        forCard(wallets, mask, bankPackage)
            ?: currency?.let { c -> wallets.firstOrNull { it.currency == c } }
            ?: wallets.firstOrNull()
}
