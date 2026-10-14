package com.wolfeleo2.subsy.data.repository

import com.wolfeleo2.subsy.data.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Units of each currency per one unit of [base]. */
@Serializable
data class ExchangeRates(
    @SerialName("base_code") val base: String,
    @SerialName("time_last_update_unix") val updatedAtEpochSeconds: Long,
    val rates: Map<String, Double>,
) {
    fun convert(
        amount: Double,
        from: String,
        to: String,
    ): Double? {
        if (from == to) return amount
        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        return amount / fromRate * toRate
    }
}

/** Live subscriptions expressed in one currency, for totals. */
data class CurrencyTotals(
    val subscriptions: List<Subscription>,
    /** Billed in another currency and converted at the cached rates. */
    val convertedCount: Int,
    /** Billed in a currency there's no rate for (offline before the first fetch, or an unlisted code). */
    val unconvertedCount: Int,
)

/** Copies each subscription into [currency] where a rate exists; the rest are counted, not summed. */
fun List<Subscription>.inCurrency(
    currency: String,
    rates: ExchangeRates?,
): CurrencyTotals {
    var converted = 0
    var unconverted = 0
    val result =
        mapNotNull { sub ->
            if (sub.currency == currency) return@mapNotNull sub
            val amount = rates?.convert(sub.amount, sub.currency, currency)
            if (amount == null) {
                unconverted++
                null
            } else {
                converted++
                sub.copy(amount = amount, currency = currency)
            }
        }
    return CurrencyTotals(result, converted, unconverted)
}

/**
 * Daily reference rates from ExchangeRate-API's keyless endpoint (attribution shown in Settings).
 * Rates are cached in a file so totals still convert offline; a failed refresh keeps the old ones.
 */
class ExchangeRateRepository(
    private val cacheFile: File,
    private val fetch: () -> String = ::download,
) {
    private val _rates = MutableStateFlow<ExchangeRates?>(null)
    val rates: StateFlow<ExchangeRates?> = _rates.asStateFlow()

    suspend fun refreshIfStale(nowEpochSeconds: Long = System.currentTimeMillis() / 1000) =
        withContext(Dispatchers.IO) {
            if (_rates.value == null) {
                _rates.value = runCatching { parse(cacheFile.readText()) }.getOrNull()
            }
            val current = _rates.value
            if (current != null && nowEpochSeconds - current.updatedAtEpochSeconds < STALE_AFTER_SECONDS) return@withContext
            runCatching {
                val body = fetch()
                val fresh = parse(body)
                cacheFile.writeText(body)
                _rates.value = fresh
            }
        }

    private companion object {
        const val URL_LATEST = "https://open.er-api.com/v6/latest/USD"

        // The source updates once a day.
        const val STALE_AFTER_SECONDS = 24 * 60 * 60L
        const val TIMEOUT_MS = 10_000

        val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): ExchangeRates = json.decodeFromString<ExchangeRates>(body).also { require(it.rates.isNotEmpty()) }

        fun download(): String {
            val connection = URL(URL_LATEST).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            return try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
