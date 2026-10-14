package com.wolfeleo2.subsy.detection

import com.wolfeleo2.subsy.data.model.DetectedKind
import com.wolfeleo2.subsy.data.model.SpendCategory
import com.wolfeleo2.subsy.data.model.TransactionType
import java.util.Currency

/** A charge the app is willing to show: the model's reading after [ChargeGuards] checked it against the message. */
data class DetectedCharge(
    val kind: DetectedKind,
    val amount: Double,
    val currency: String,
    val merchant: String?,
    val reference: String?,
    /** A [SpendCategory] name; a first guess only. */
    val categoryKey: String?,
)

/**
 * The cheap gate in front of the model, run on every SMS and notification: a digit and a currency marker. Anything
 * else (most chats, OTPs without an amount) is dropped there and never stored or queued.
 */
fun looksLikeMoney(text: String): Boolean = text.any(Char::isDigit) && CurrencyMarkers.findIn(text) != null

/**
 * Currency markers in messages. ISO codes count only in capitals ("KES", "EUR"), since lowercase codes collide with
 * words ("all", "top", "try"); local spellings and symbols count in any case. A bare "$" is read as USD.
 */
object CurrencyMarkers {
    private val aliases =
        mapOf(
            "ksh" to "KES",
            "kshs" to "KES",
            "$" to "USD",
            "us$" to "USD",
            "£" to "GBP",
            "€" to "EUR",
            "₦" to "NGN",
            "₹" to "INR",
            "rs" to "INR",
            "¥" to "JPY",
        )
    private val isoCodes = Currency.getAvailableCurrencies().map { it.currencyCode }.toSet()
    private val symbolPattern = Regex("""us\$|[$£€₦₹¥]""", RegexOption.IGNORE_CASE)
    private val wordPattern = Regex("""(?<![A-Za-z])([A-Za-z]{2,4})(?![A-Za-z])""")

    /** The first currency in [text]: an alias or symbol in any case, or an ISO code in capitals. */
    fun findIn(
        text: String,
        anyCaseIso: Boolean = false,
    ): String? {
        val candidates =
            symbolPattern.findAll(text).map { it.range.first to aliases.getValue(it.value.lowercase()) } +
                wordPattern.findAll(text).mapNotNull { match ->
                    val word = match.groupValues[1]
                    val code =
                        aliases[word.lowercase()]
                            ?: word.takeIf { (anyCaseIso || it == it.uppercase()) && it.uppercase() in isoCodes }?.uppercase()
                    code?.let { match.range.first to it }
                }
        return candidates.minByOrNull { it.first }?.second
    }
}

/** The app's rules on top of the model (see ARCHITECTURE.md, "App-side guards"); the model reads, these decide. */
object ChargeGuards {
    // Labels of the detector-v1 export (spec.json); a new export with new labels needs these updated.
    private val kinds =
        mapOf(
            "payment or money sent" to DetectedKind.EXPENSE,
            "money received or refund" to DetectedKind.INCOME,
            "transfer between own accounts" to DetectedKind.TRANSFER,
        )
    private val categories =
        mapOf(
            "supermarket or groceries" to SpendCategory.GROCERIES,
            "restaurant, cafe or coffee" to SpendCategory.DINING,
            "taxi, fuel or fares" to SpendCategory.TRANSPORT,
            "rent, electricity, water or home internet" to SpendCategory.HOUSING,
            "airtime, data bundles or phone plan" to SpendCategory.MOBILE,
            "shopping" to SpendCategory.SHOPPING,
            "health or pharmacy" to SpendCategory.HEALTH,
            "streaming, TV or games" to SpendCategory.ENTERTAINMENT,
            "travel" to SpendCategory.TRAVEL,
            "education" to SpendCategory.EDUCATION,
            "gift" to SpendCategory.GIFTS,
            "salary" to SpendCategory.SALARY,
            "refund or reversal" to SpendCategory.REFUNDS,
        )

    /** A leading masked phone number fused to a name by a hyphen, as in KCB transfers: "2547****304-JOHN KAMAU". */
    private val maskedNumberPrefix = Regex("""^\d+\*+\d*-""")

    /** A usable transaction reference: one token with letters or digits mixed, long enough not to repeat by chance. */
    private val referenceShape = Regex("""[A-Za-z0-9.]{6,}""")

    /** Null when the message isn't a completed money movement the app can trust. */
    fun accept(
        reading: ModelReading,
        message: String,
    ): DetectedCharge? {
        val kind = kinds[reading.kind] ?: return null
        val amountText = reading.entities["amount"]?.firstOrNull() ?: return null
        val amount = parseMessageAmount(amountText)?.takeIf { it > 0 } ?: return null
        // The amount span usually carries its marker ("Ksh1,100.00"); otherwise the message's first marker.
        val currency = CurrencyMarkers.findIn(amountText, anyCaseIso = true) ?: CurrencyMarkers.findIn(message) ?: return null
        val merchant =
            reading.entities["merchant"]
                ?.firstOrNull()
                ?.replace(maskedNumberPrefix, "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        // A junk "reference" shared by many messages (an app name) would make real charges look like duplicates.
        val reference =
            reading.entities["reference"]?.firstOrNull { ref ->
                referenceShape.matches(ref) && ref.any(Char::isDigit) && ref.any(Char::isLetter)
            }
        val category =
            categories[reading.category]
                ?.takeIf { (it.kind == TransactionType.INCOME) == (kind == DetectedKind.INCOME) }
        return DetectedCharge(kind, amount, currency, merchant, reference, category?.name)
    }
}

/**
 * The number in an amount as printed in a message: "Ksh1,100.00" is 1100, "23,40 EUR" is 23.4, "1.234,56" is
 * 1234.56. With both separators the later one is the decimal; a lone comma is a decimal only before exactly two
 * digits. Port of parse_amount in eval/run_eval.py.
 */
fun parseMessageAmount(text: String): Double? {
    val match = Regex("""\d[\d.,\s]*\d|\d""").find(text) ?: return null
    var number = match.value.filterNot(Char::isWhitespace)
    val comma = number.lastIndexOf(',')
    val dot = number.lastIndexOf('.')
    number =
        when {
            comma >= 0 && dot >= 0 ->
                if (comma > dot) number.replace(".", "").replace(',', '.') else number.replace(",", "")
            comma >= 0 -> if (Regex(""",\d{2}$""").containsMatchIn(number)) number.replace(',', '.') else number.replace(",", "")
            else -> number
        }
    return number.toDoubleOrNull()
}
