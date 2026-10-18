package com.example.aws_g4

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopRepositoryTest {

    private val repo = ShopRepository(FakeShopApi())

    @Test
    fun items_returns_success_with_nine_items() = runTest {
        val result = repo.items()

        assertTrue(result.isSuccess)
        assertEquals(9, result.getOrNull()?.size)
    }

    @Test
    fun placeOrder_returns_orderId() = runTest {
        val id = "55555555-5555-4555-8555-555555555555"

        val result = repo.placeOrder(
            orderId = id,
            cart = mapOf(1 to 2, 3 to 1),
            simulate = SimulateMode.NONE
        )

        assertTrue(result.isSuccess)
        assertEquals(id, result.getOrNull())
    }

    @Test
    fun placeOrder_with_empty_cart_returns_failure() = runTest {
        val result = repo.placeOrder(
            orderId = "66666666-6666-4666-8666-666666666666",
            cart = emptyMap(),
            simulate = SimulateMode.NONE
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun orders_returns_seeded_history() = runTest {
        val result = repo.orders()

        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
    }

    @Test
    fun placeOrder_with_unknown_item_returns_failure() = runTest {
        val result = repo.placeOrder(
            orderId = "88888888-8888-4888-8888-888888888888",
            cart = mapOf(99999 to 1),
            simulate = SimulateMode.NONE
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun items_returns_items_with_valid_ids() = runTest {
        val result = repo.items()
        val items = result.getOrThrow()

        assertTrue(items.all { it.id > 0 })
    }

    @Test
    fun orders_contains_failed_seeded_order() = runTest {
        val result = repo.orders()
        val orders = result.getOrThrow()

        assertTrue(orders.any { it.status == "FAILED" })
    }
}
