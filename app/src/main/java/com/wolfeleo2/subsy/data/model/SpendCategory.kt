package com.wolfeleo2.subsy.data.model

/** Colors offered for custom categories; each holds 3:1 against white so the icon on it stays visible. */
val CustomCategoryColors = listOf("#0F766E", "#1D4ED8", "#7E22CE", "#BE123C", "#C2410C", "#4D7C0F", "#A16207", "#334155")

/**
 * Built-in categories for transactions, stored by name. [SUBSCRIPTIONS] is not offered when logging: it is where
 * subscription payments land in the all-spending totals. Colors hold 3:1 against white for the icon drawn on them.
 */
enum class SpendCategory(
    val kind: TransactionType,
    val colorHex: String,
    val pickable: Boolean = true,
) {
    GROCERIES(TransactionType.EXPENSE, "#16A34A"),
    DINING(TransactionType.EXPENSE, "#EA580C"),
    TRANSPORT(TransactionType.EXPENSE, "#2563EB"),
    HOUSING(TransactionType.EXPENSE, "#0891B2"),
    MOBILE(TransactionType.EXPENSE, "#9333EA"),
    SHOPPING(TransactionType.EXPENSE, "#DB2777"),
    HEALTH(TransactionType.EXPENSE, "#059669"),
    ENTERTAINMENT(TransactionType.EXPENSE, "#8B5CF6"),
    TRAVEL(TransactionType.EXPENSE, "#0369A1"),
    EDUCATION(TransactionType.EXPENSE, "#B45309"),
    GIFTS(TransactionType.EXPENSE, "#E11D48"),

    // Money sent to people: paying a friend back, supporting family. Gifts stays for actual gifts.
    FRIENDS_FAMILY(TransactionType.EXPENSE, "#A21CAF"),
    OTHER(TransactionType.EXPENSE, "#64748B"),
    SUBSCRIPTIONS(TransactionType.EXPENSE, "#4F46E5", pickable = false),
    SALARY(TransactionType.INCOME, "#15803D"),
    FREELANCE(TransactionType.INCOME, "#0D9488"),
    REFUNDS(TransactionType.INCOME, "#0284C7"),
    GIFTS_RECEIVED(TransactionType.INCOME, "#BE185D"),
    FRIENDS_FAMILY_INCOME(TransactionType.INCOME, "#A21CAF"),
    INVESTMENTS(TransactionType.INCOME, "#A16207"),
    OTHER_INCOME(TransactionType.INCOME, "#64748B"),
    ;

    companion object {
        fun fromKey(key: String?): SpendCategory? = entries.firstOrNull { it.name == key }

        fun fallbackFor(kind: TransactionType): SpendCategory = if (kind == TransactionType.INCOME) OTHER_INCOME else OTHER

        fun pickable(kind: TransactionType): List<SpendCategory> = entries.filter { it.kind == kind && it.pickable }
    }
}

/** What a stored category key points at. A key whose custom category was deleted reads as the kind's Other. */
sealed interface CategoryRef {
    val key: String
    val colorHex: String

    data class Builtin(
        val category: SpendCategory,
    ) : CategoryRef {
        override val key: String get() = category.name
        override val colorHex: String get() = category.colorHex
    }

    data class Custom(
        val category: CustomCategory,
    ) : CategoryRef {
        override val key: String get() = category.key
        override val colorHex: String get() = category.colorHex
    }
}

fun resolveCategory(
    key: String,
    kind: TransactionType,
    customs: Map<String, CustomCategory>,
): CategoryRef =
    SpendCategory.fromKey(key)?.let { CategoryRef.Builtin(it) }
        ?: customs[key]?.let { CategoryRef.Custom(it) }
        ?: CategoryRef.Builtin(SpendCategory.fallbackFor(kind))
