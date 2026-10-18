package com.example.aws_g4

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class FakeShopApiTest {

    @Test
    fun getItems_returns_nine_items() = runTest {
        val items = FakeShopApi().getItems()

        assertEquals(9, items.size)
    }

    @Test
    fun getItems_has_a_broken_image_for_edge_case() = runTest {
        val items = FakeShopApi().getItems()

        assertTrue(
            items.any {
                it.imageUrl.startsWith("https://example.invalid")
            }
        )
    }

    @Test
    fun placeOrder_is_idempotent() = runTest {
        val api = FakeShopApi()
        val orderId = "11111111-1111-4111-8111-111111111111"

        val request = PlaceOrderRequest(
            orderId = orderId,
            lines = listOf(OrderLineRequest(1, 1)),
            simulate = null
        )

        api.placeOrder(request)
        api.placeOrder(request)

        val orders = api.getOrders()

        assertEquals(1, orders.count { it.orderId == orderId })
    }

    @Test(expected = HttpException::class)
    fun placeOrder_rejects_empty_lines() = runTest {
        FakeShopApi().placeOrder(
            PlaceOrderRequest(
                orderId = "22222222-2222-4222-8222-222222222222",
                lines = emptyList(),
                simulate = null
            )
        )
    }

    @Test(expected = HttpException::class)
    fun placeOrder_rejects_unknown_item() = runTest {
        FakeShopApi().placeOrder(
            PlaceOrderRequest(
                orderId = "33333333-3333-4333-8333-333333333333",
                lines = listOf(OrderLineRequest(99999, 1)),
                simulate = null
            )
        )
    }

    @Test(expected = HttpException::class)
    fun placeOrder_rejects_zero_quantity() = runTest {
        FakeShopApi().placeOrder(
            PlaceOrderRequest(
                orderId = "44444444-4444-4444-8444-444444444444",
                lines = listOf(OrderLineRequest(1, 0)),
                simulate = null
            )
        )
    }

    @Test(expected = HttpException::class)
    fun placeOrder_rejects_negative_quantity() = runTest {
        FakeShopApi().placeOrder(
            PlaceOrderRequest(
                orderId = "55555555-5555-4555-8555-555555555555",
                lines = listOf(OrderLineRequest(1, -1)),
                simulate = null
            )
        )
    }

    @Test
    fun order_history_has_three_seeded_orders() = runTest {
        val orders = FakeShopApi().getOrders()

        assertEquals(3, orders.size)
    }

    @Test
    fun seeded_history_contains_a_failed_order() = runTest {
        val orders = FakeShopApi().getOrders()

        assertTrue(orders.any { it.status == "FAILED" })
    }

    @Test
    fun seeded_history_contains_completed_orders() = runTest {
        val orders = FakeShopApi().getOrders()

        assertTrue(orders.any { it.status == "DONE" })
    }

    @Test
    fun placeOrder_returns_the_requested_order_id() = runTest {
        val api = FakeShopApi()
        val orderId = "66666666-6666-4666-8666-666666666666"

        val response = api.placeOrder(
            PlaceOrderRequest(
                orderId = orderId,
                lines = listOf(OrderLineRequest(1, 2)),
                simulate = null
            )
        )

        assertEquals(orderId, response.orderId)
    }

    @Test
    fun getOrders_includes_a_newly_placed_order() = runTest {
        val api = FakeShopApi()
        val orderId = "77777777-7777-4777-8777-777777777777"

        api.placeOrder(
            PlaceOrderRequest(
                orderId = orderId,
                lines = listOf(OrderLineRequest(1, 2)),
                simulate = null
            )
        )

        val orders = api.getOrders()

        assertTrue(orders.any { it.orderId == orderId })
    }
}
