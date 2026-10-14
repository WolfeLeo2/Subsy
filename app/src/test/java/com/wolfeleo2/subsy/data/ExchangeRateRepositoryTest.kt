package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.repository.ExchangeRateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExchangeRateRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun body(updatedAt: Long) =
        """{"result":"success","base_code":"USD","time_last_update_unix":$updatedAt,"rates":{"USD":1,"KES":129.5}}"""

    @Test
    fun fetchesParsesAndCaches() =
        runTest {
            val file = tmp.newFile()
            ExchangeRateRepository(file) { body(100) }.refreshIfStale(nowEpochSeconds = 200)

            // A new instance reads the cache and doesn't fetch while it is fresh.
            val cached = ExchangeRateRepository(file) { error("should not fetch") }
            cached.refreshIfStale(nowEpochSeconds = 300)
            assertEquals(1295.0, cached.rates.value!!.convert(10.0, "USD", "KES")!!, 0.001)
        }

    @Test
    fun failedRefreshKeepsStaleRates() =
        runTest {
            val file = tmp.newFile()
            ExchangeRateRepository(file) { body(100) }.refreshIfStale(nowEpochSeconds = 200)

            val offline = ExchangeRateRepository(file) { error("offline") }
            offline.refreshIfStale(nowEpochSeconds = 100 + 2 * 24 * 60 * 60)
            assertEquals(100L, offline.rates.value!!.updatedAtEpochSeconds)
        }

    @Test
    fun unknownCurrencyDoesNotConvert() =
        runTest {
            val repo = ExchangeRateRepository(tmp.newFile()) { body(100) }
            repo.refreshIfStale(nowEpochSeconds = 200)
            assertNull(repo.rates.value!!.convert(10.0, "XYZ", "KES"))
        }
}
