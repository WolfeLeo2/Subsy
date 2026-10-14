package com.wolfeleo2.subsy.detection

import com.wolfeleo2.subsy.data.model.DetectedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeGuardsTest {
    @Test
    fun parsesAmountsAsPrintedInMessages() {
        assertEquals(1100.0, parseMessageAmount("Ksh1,100.00")!!, 0.0)
        assertEquals(23.4, parseMessageAmount("23,40 EUR")!!, 0.0)
        assertEquals(1234.56, parseMessageAmount("1.234,56 EUR")!!, 0.0)
        assertEquals(45000.0, parseMessageAmount("KES 45,000.00")!!, 0.0)
        assertEquals(3000.0, parseMessageAmount("KES 3000.0")!!, 0.0)
        assertEquals(249.0, parseMessageAmount("Rs.249.00")!!, 0.0)
        assertNull(parseMessageAmount("Ksh"))
    }

    @Test
    fun findsCurrencyMarkers() {
        assertEquals("KES", CurrencyMarkers.findIn("Ksh20.00 sent to SAFARICOM"))
        assertEquals("KES", CurrencyMarkers.findIn("KES 450.00 received from JOHN KAMAU"))
        assertEquals("USD", CurrencyMarkers.findIn("A charge of $15.49 at NETFLIX.COM"))
        assertEquals("EUR", CurrencyMarkers.findIn("Kartenzahlung 23,40 EUR bei REWE"))
        assertEquals("NGN", CurrencyMarkers.findIn("Amt: NGN5,000.00 DR"))
        assertEquals("INR", CurrencyMarkers.findIn("Rs.249.00 debited"))
        // Lowercase ISO-looking words are ordinary words outside an amount.
        assertNull(CurrencyMarkers.findIn("all 3 of us will try to top it up"))
        assertEquals("EUR", CurrencyMarkers.findIn("23,40 eur", anyCaseIso = true))
    }

    @Test
    fun preFilterKeepsMoneyAndDropsTheRest() {
        assertTrue(looksLikeMoney("UJ80N9LJJV Confirmed. Ksh20.00 sent to SAFARICOM DATA BUNDLES"))
        assertTrue(looksLikeMoney("Paid €4.50 at Starbucks"))
        assertFalse(looksLikeMoney("Your verification code is 482913. Do not share it."))
        assertFalse(looksLikeMoney("Lunch tomorrow? I owe you like 1,500 from last week"))
        assertFalse(looksLikeMoney("Ksh balance query: dial the usual code"))
    }

    @Test
    fun acceptsAChargeTheMessageBacksUp() {
        val charge =
            ChargeGuards.accept(
                reading(kind = "payment or money sent", amount = "Ksh1,100.00", merchant = "NETFLIX.COM", reference = "UJE1NB2XYZ"),
                "UJE1NB2XYZ Confirmed. Ksh1,100.00 sent to M-PESA CARD for account PAYPAL *NETFLIX.COM",
            )!!
        assertEquals(DetectedKind.EXPENSE, charge.kind)
        assertEquals(1100.0, charge.amount, 0.0)
        assertEquals("KES", charge.currency)
        assertEquals("NETFLIX.COM", charge.merchant)
        assertEquals("UJE1NB2XYZ", charge.reference)
        assertEquals("ENTERTAINMENT", charge.categoryKey)
    }

    @Test
    fun dropsMessagesThatAreNotCompletedMoneyMovements() {
        val other = reading(kind = "promotion, code, loan, failed transaction or chat", amount = "KES 300K")
        assertNull(ChargeGuards.accept(other, "Pata extra cash with a Mobile Loan of up to KES 300K."))
        val noAmount = reading(kind = "payment or money sent", amount = null)
        assertNull(ChargeGuards.accept(noAmount, "You paid Spotify"))
    }

    @Test
    fun stripsMaskedNumbersAndIgnoresJunkReferences() {
        val charge =
            ChargeGuards.accept(
                reading(kind = "payment or money sent", amount = "KES 350.00", merchant = "2547****304-JOHN KAMAU", reference = "KCB App"),
                "Dear JANE you have successfully transferred KES 350.00 to  2547****304-JOHN KAMAU on 21/09/2026",
            )!!
        assertEquals("JOHN KAMAU", charge.merchant)
        // "KCB App" would match every KCB message and make real charges look like duplicates.
        assertNull(charge.reference)
    }

    @Test
    fun keepsTheCategoryOnlyWhenItFitsTheDirection() {
        val salaryAsExpense =
            ChargeGuards.accept(
                reading(kind = "payment or money sent", amount = "KES 99.00", category = "salary"),
                "Paid KES 99.00",
            )!!
        assertNull(salaryAsExpense.categoryKey)
    }

    private fun reading(
        kind: String,
        amount: String?,
        merchant: String? = null,
        reference: String? = null,
        category: String = "streaming, TV or games",
    ) = ModelReading(
        entities = mapOf("amount" to listOfNotNull(amount), "merchant" to listOfNotNull(merchant), "reference" to listOfNotNull(reference)),
        kind = kind,
        category = category,
    )
}
