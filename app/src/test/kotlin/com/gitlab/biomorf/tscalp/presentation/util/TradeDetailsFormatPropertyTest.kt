package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.usecases.TradeDetails
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderFormState
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Property-based tests for [formatCostOverlay] / [formatMultiplierOverlay]
 * (kotest-property). Edge `Double` inputs (NaN / Infinity / -0.0 / MAX /
 * MIN / MIN_NORMAL) plus `null` for nullable fields.
 *
 * Invariants:
 * - Neither function throws on any input.
 * - Null result iff no usable fields (cost: `points+domestic` OR `currency`;
 *   multiplier: same shape).
 * - Non-null result is a non-empty string.
 */
class TradeDetailsFormatPropertyTest {
    private val edgeDoubles =
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            0.0,
            -0.0,
            1.0,
            -1.0,
            Double.MAX_VALUE,
            Double.MIN_VALUE,
        )

    private val safeInstrument =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Sber",
            isin = "RU0009029540",
        )

    private val state = OrderFormState(selectedInstrument = safeInstrument)

    private fun edgeOrNull(): Arb<Double?> = Arb.element(edgeDoubles).orNull()

    @Test
    fun `property 3 - formatCostOverlay does not throw on any Double input`() {
        runBlocking {
            checkAll(edgeOrNull(), edgeOrNull(), edgeOrNull()) { cp, cc, cd ->
                val details =
                    TradeDetails(
                        executionPrice = 0.0,
                        costPoints = cp,
                        costCurrency = cc,
                        costDomesticCurrency = cd,
                    )
                formatCostOverlay(details, state.selectedInstrument)
            }
        }
    }

    @Test
    fun `property 4 - formatCostOverlay null iff no usable cost fields`() {
        runBlocking {
            checkAll(edgeOrNull(), edgeOrNull(), edgeOrNull()) { cp, cc, cd ->
                val details =
                    TradeDetails(
                        executionPrice = 0.0,
                        costPoints = cp,
                        costCurrency = cc,
                        costDomesticCurrency = cd,
                    )
                val result = formatCostOverlay(details, state.selectedInstrument)
                val expectedNonNull = (cp != null && cd != null) || cc != null
                if (expectedNonNull) {
                    assertTrue("expected non-null, got null", result != null)
                } else {
                    assertTrue("expected null, got '$result'", result == null)
                }
            }
        }
    }

    @Test
    fun `property 5 - formatMultiplierOverlay null iff no usable multiplier fields`() {
        runBlocking {
            checkAll(edgeOrNull(), edgeOrNull(), edgeOrNull()) { mp, mc, md ->
                val details =
                    TradeDetails(
                        executionPrice = 0.0,
                        multiplierPoints = mp,
                        multiplierCurrency = mc,
                        multiplierDomesticCurrency = md,
                    )
                val result = formatMultiplierOverlay(details, state.pairedInstrument)
                val expectedNonNull = (mp != null && md != null) || mc != null
                if (expectedNonNull) {
                    assertTrue("expected non-null, got null", result != null)
                } else {
                    assertTrue("expected null, got '$result'", result == null)
                }
            }
        }
    }

    @Test
    fun `property 6 - non-null formatCostOverlay is non-empty`() {
        runBlocking {
            checkAll(edgeOrNull(), edgeOrNull(), edgeOrNull()) { cp, cc, cd ->
                val details =
                    TradeDetails(
                        executionPrice = 0.0,
                        costPoints = cp,
                        costCurrency = cc,
                        costDomesticCurrency = cd,
                    )
                val result = formatCostOverlay(details, state.selectedInstrument)
                if (result != null) {
                    assertTrue("expected non-empty, got '$result'", result.isNotEmpty())
                }
            }
        }
    }
}
