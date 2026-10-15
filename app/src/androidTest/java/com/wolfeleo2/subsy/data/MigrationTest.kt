package com.wolfeleo2.subsy.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wolfeleo2.subsy.data.local.db.SubsyDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Upgrading must keep every subscription and payment; run on a device with connectedDebugAndroidTest. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SubsyDatabase::class.java)

    @Test
    fun migrate1To2KeepsSubscriptionsAndPayments() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO subscriptions (id, name, category, amount, currency, billing_cycle, start_date, next_billing_date,
                    is_free_trial, trial_end_date, status, cancellation_url, icon_name, color_hex, notes, created_at)
                VALUES ('s1', 'Netflix', 'ENTERTAINMENT', 15.99, 'USD', 'MONTHLY', 1000, 2000, 0, NULL, 'ACTIVE', NULL, NULL, NULL, NULL, 1000)
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO payment_records (id, subscription_id, amount, currency, payment_date, status) " +
                    "VALUES ('p1', 's1', 15.99, 'USD', 1500, 'AUTO_LOGGED')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true)

        db.query("SELECT COUNT(*) FROM subscriptions").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM payment_records").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM transactions").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrate2To3KeepsTransactionsAndCategories() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO transactions (id, type, amount, currency, date, category_key, merchant, note, source, created_at) " +
                    "VALUES ('t1', 'EXPENSE', 12.5, 'USD', 1000, 'custom:c1', NULL, 'Lunch', 'MANUAL', 1000)",
            )
            execSQL(
                "INSERT INTO custom_categories (id, name, kind, color_hex, created_at) VALUES ('c1', 'Coffee', 'EXPENSE', '#0F766E', 1000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true)

        db.query("SELECT COUNT(*) FROM transactions").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM custom_categories").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM category_budgets").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun migrate3To4KeepsBudgetsAndLeavesTheirCurrencyToBePinned() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL("INSERT INTO category_budgets (category_key, amount) VALUES ('DINING', 50.0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true)

        db.query("SELECT amount, currency FROM category_budgets").use {
            it.moveToFirst()
            assertEquals(50.0, it.getDouble(0), 0.001)
            assertEquals("", it.getString(1))
        }
    }

    @Test
    fun migrate4To5KeepsDetectionsAndDefaultsTheNewColumns() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO detected_transactions (id, merchant_name, amount, raw_message, source, detected_at, is_processed) " +
                    "VALUES ('d1', 'Netflix', 1100.0, 'Ksh1,100.00 sent to NETFLIX', 'SMS', 1000, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true)

        db.query("SELECT amount, currency, kind, reference FROM detected_transactions").use {
            it.moveToFirst()
            assertEquals(1100.0, it.getDouble(0), 0.001)
            assertEquals("", it.getString(1))
            assertEquals("EXPENSE", it.getString(2))
            assertEquals(true, it.isNull(3))
        }
    }

    @Test
    fun migrate5To6KeepsSubscriptionsAndPaymentsWithTheNewColumnsEmpty() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO subscriptions (id, name, category, amount, currency, billing_cycle, start_date, next_billing_date,
                    is_free_trial, trial_end_date, status, cancellation_url, icon_name, color_hex, notes, created_at)
                VALUES ('s1', 'Rent', 'OTHER', 25000.0, 'KES', 'MONTHLY', 1000, 2000, 0, NULL, 'ACTIVE', NULL, NULL, NULL, NULL, 1000)
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO payment_records (id, subscription_id, amount, currency, payment_date, status) " +
                    "VALUES ('p1', 's1', 25000.0, 'KES', 1500, 'CONFIRMED')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true)

        db.query("SELECT spend_category, bank_name, archived_at FROM subscriptions").use {
            it.moveToFirst()
            assertEquals(true, it.isNull(0) && it.isNull(1) && it.isNull(2))
        }
        db.query("SELECT COUNT(*) FROM payment_records").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
