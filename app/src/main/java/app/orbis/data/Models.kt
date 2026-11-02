package app.orbis.data

import kotlinx.serialization.Serializable

/** All amounts are stored in cents (1/100 of a unit) to avoid rounding errors. */

@Serializable
data class Account(
    val id: String,
    val name: String,
    val pinHash: String,
    val pinSalt: String,
    val balance: Long,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val biometric: Boolean = false,
    val pinForTransfers: Boolean = true,
    val dailyLimit: Long = 0, // 0 = no limit
    val anonymousInLive: Boolean = false,
    val bio: String = "",
)

@Serializable
data class Contact(
    val id: String,
    val owner: String,
    val accountId: String,
    val name: String,
    val favorite: Boolean = false,
    val note: String = "",
)

@Serializable
enum class TxnKind { TRANSFER, TOPUP, GOAL_IN, GOAL_OUT, REQUEST, RECURRING }

@Serializable
data class Txn(
    val id: String,
    val from: String? = null,
    val to: String? = null,
    val fromName: String = "",
    val toName: String = "",
    val amount: Long,
    val note: String = "",
    val category: String = Category.OTHER.key,
    val time: Long = System.currentTimeMillis(),
    val kind: TxnKind = TxnKind.TRANSFER,
)

@Serializable
enum class RequestStatus { PENDING, PAID, DECLINED, CANCELLED }

@Serializable
data class PayRequest(
    val id: String,
    val requester: String,
    val requesterName: String,
    val payer: String,
    val payerName: String,
    val amount: Long,
    val note: String = "",
    val time: Long = System.currentTimeMillis(),
    val status: RequestStatus = RequestStatus.PENDING,
    val groupId: String? = null,
)

@Serializable
data class Goal(
    val id: String,
    val owner: String,
    val name: String,
    val emoji: String = "🎯",
    val target: Long,
    val saved: Long = 0,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Budget(
    val owner: String,
    val category: String,
    val limit: Long,
)

@Serializable
enum class Interval(val label: String, val days: Int) {
    DAILY("Täglich", 1), WEEKLY("Wöchentlich", 7), MONTHLY("Monatlich", 30)
}

@Serializable
data class Recurring(
    val id: String,
    val owner: String,
    val toId: String,
    val toName: String,
    val amount: Long,
    val note: String = "",
    val category: String = Category.OTHER.key,
    val interval: Interval = Interval.MONTHLY,
    val nextRun: Long,
    val active: Boolean = true,
    val runs: Int = 0,
)

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel"), AMOLED("AMOLED") }

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0,
    val currencySymbol: String = "₳",
    val currencyName: String = "Aurel",
    val symbolBefore: Boolean = false,
    val idPrefix: String = "OR",
    val hideBalance: Boolean = false,
    val blockScreenshots: Boolean = false,
    val haptics: Boolean = true,
    val autoLockSeconds: Int = 60,
    val lastAccount: String? = null,
)

@Serializable
data class BankState(
    val version: Int = 1,
    val accounts: List<Account> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val txns: List<Txn> = emptyList(),
    val requests: List<PayRequest> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val settings: Settings = Settings(),
) {
    fun account(id: String?): Account? = accounts.firstOrNull { it.id == id }
}

enum class Category(val key: String, val label: String, val emoji: String, val color: Long) {
    FOOD("food", "Essen", "🍔", 0xFFFF8A4C),
    SHOPPING("shopping", "Shopping", "🛍️", 0xFFEC4899),
    TRANSPORT("transport", "Mobilität", "🚗", 0xFF3B82F6),
    FUN("fun", "Freizeit", "🎮", 0xFFA855F7),
    HOME("home", "Wohnen", "🏠", 0xFF14B8A6),
    HEALTH("health", "Gesundheit", "💊", 0xFFEF4444),
    EDUCATION("education", "Bildung", "📚", 0xFFF59E0B),
    GIFTS("gifts", "Geschenke", "🎁", 0xFFF472B6),
    SALARY("salary", "Gehalt", "💼", 0xFF22C55E),
    OTHER("other", "Sonstiges", "✨", 0xFF94A3B8);

    companion object {
        fun of(key: String): Category = entries.firstOrNull { it.key == key } ?: OTHER
    }
}
