package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderFormState
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Property-based tests for [toPlaceOrderRequest] (kotest-property).
 *
 * Invariants:
 * - null in any guard (`selectedInstrument` / `quantityAsLong`) -> null result.
 * - both guards valid -> non-null result with quantity preserved and
 *   instrument identity preserved.
 */
class OrderFormStateMapperPropertyTest {
    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Sber",
            isin = "RU0009029540",
        )

    @Test
    fun `property 1 - null selectedInstrument or quantityAsLong yields null`() {
        runBlocking {
            checkAll(Arb.boolean(), Arb.boolean()) { noInstrument, noQuantity ->
                if (!noInstrument && !noQuantity) return@checkAll
                val state =
                    OrderFormState(
                        selectedInstrument = if (noInstrument) null else sber,
                        quantity = if (noQuantity) "" else "3",
                    )
                assertNull(state.toPlaceOrderRequest())
            }
        }
    }

    @Test
    fun `property 2 - valid instrument and positive quantity yields non-null with same quantity`() {
        runBlocking {
            checkAll(Arb.long(1L, 1_000_000L)) { qty ->
                val state =
                    OrderFormState(
                        selectedInstrument = sber,
                        quantity = qty.toString(),
                    )
                val result = state.toPlaceOrderRequest()
                assertNotNull(result)
                assertEquals(qty, result!!.quantity)
                assertEquals(sber.tscalpInstrumentId, result.instrument.tscalpInstrumentId)
            }
        }
    }
}
