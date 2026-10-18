package com.example.aws_g4

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory fake backend for UI work while the real AWS backend does not exist yet.
 * Enabled by USE_FAKE_API in ShopClient (ShopApi.kt). Data resets when the app process restarts.
 *
 * The catalog imitates a wholesale food-service supplier: prices are per case / box / sack,
 * and basket quantities are numbers of cases.
 *
 * Things you can try:
 *  - Login: any username/password works. Use the password "wrong" to see the 401 error state.
 *  - Catalog: 9 items. #8 has a very long name, #9 has a broken image URL (to check edge cases).
 *  - Orders: the status advances over time, driven by the developer option in the basket:
 *      Process normally   -> Received -> Processing -> Confirmed          (about 3s)
 *      Temporary failure  -> Processing (retried behind the scenes) -> Confirmed   (about 10s)
 *      Permanent failure  -> Processing -> FAILED (dead-letter queue)     (about 10s)
 *    In every case the customer first sees the "order placed" confirmation screen.
 *  - History: 3 seeded orders (two confirmed, one that ended up in the DLQ).
 */
class FakeShopApi : ShopApi {

    private class FakeOrder(
        val orderId: String,
        val createdAtMs: Long,
        val lines: List<OrderLine>,
        val mode: String?,                 // null / "TRANSIENT" / "POISON"
        val fixedStatus: String? = null,   // set for seeded history orders
        val fixedAttempts: Int = 0
    ) {
        val total: Double get() = lines.sumOf { it.unitPrice * it.quantity }
    }

    // Prices are per case / box / sack (wholesale).
    private val items = listOf(
        Item(1, "Extra Virgin Olive Oil 1L (Case of 12)", "https://picsum.photos/seed/oliveoil/400/400", 96.00),
        Item(2, "Arabica Coffee Beans 1kg (Case of 6)", "https://picsum.photos/seed/coffeebeans/400/400", 84.00),
        Item(3, "Oat Barista Drink 1L (Case of 12)", "https://picsum.photos/seed/oatdrink/400/400", 27.60),
        Item(4, "Paper Cups 8oz (Box of 1,000)", "https://picsum.photos/seed/papercups/400/400", 42.50),
        Item(5, "Compostable Takeaway Boxes (Carton of 500)", "https://picsum.photos/seed/takeaway/400/400", 58.00),
        Item(6, "Organic Wheat Flour 25kg (Sack)", "https://picsum.photos/seed/flour/400/400", 31.00),
        Item(7, "Sparkling Water 0.33L (Tray of 24)", "https://picsum.photos/seed/sparkling/400/400", 11.80),
        Item(8, "Premium Single-Origin Cold Brew Concentrate 5L Bag-in-Box with Tap (Case of 4)", "https://picsum.photos/seed/coldbrew/400/400", 119.00),
        Item(9, "Seasonal Sample Pack (broken image test)", "https://example.invalid/missing.png", 25.00)
    )

    private val orders = mutableListOf<FakeOrder>()
    private val lock = Any()

    init {
        seedHistory()
    }

    // ---------------- ShopApi ----------------

    override suspend fun getItems(): List<Item> {
        delay(NETWORK_DELAY_MS)
        return items
    }

    override suspend fun placeOrder(body: PlaceOrderRequest): PlaceOrderResponse {
        delay(NETWORK_DELAY_MS)
        val invalid = body.lines.isEmpty() ||
                body.lines.any { l -> l.quantity <= 0 || items.none { it.id == l.itemId } }
        if (invalid) throw httpError(400)

        synchronized(lock) {
            // Idempotent: the same orderId never creates a second order.
            if (orders.none { it.orderId == body.orderId }) {
                orders += FakeOrder(
                    orderId = body.orderId,
                    createdAtMs = System.currentTimeMillis(),
                    lines = body.lines.map { l ->
                        val item = items.first { it.id == l.itemId }
                        OrderLine(item.id, item.name, l.quantity, item.price)
                    },
                    mode = body.simulate
                )
            }
        }
        return PlaceOrderResponse(body.orderId)
    }

    override suspend fun getOrders(): List<OrderSummary> {
        delay(NETWORK_DELAY_MS / 2)
        synchronized(lock) {
            return orders.sortedByDescending { it.createdAtMs }.map { o ->
                val (status, attempts) = snapshot(o)
                OrderSummary(
                    orderId = o.orderId,
                    status = status,
                    attempts = attempts,
                    createdAt = formatTime(o.createdAtMs),
                    total = o.total,
                    lines = o.lines
                )
            }
        }
    }

    // ---------------- Internals ----------------

    /** Computes an order's status from elapsed time, mimicking SQS retries and the DLQ. */
    private fun snapshot(o: FakeOrder): Pair<String, Int> {
        o.fixedStatus?.let { return it to o.fixedAttempts }
        val t = System.currentTimeMillis() - o.createdAtMs
        return when (o.mode) {
            "TRANSIENT", "POISON" -> when {
                t < 1_000 -> "RECEIVED" to 0
                t < 4_000 -> "PROCESSING" to 1
                t < 7_000 -> "PROCESSING" to 2
                t < 10_000 -> "PROCESSING" to 3
                else -> (if (o.mode == "POISON") "FAILED" else "DONE") to 3
            }
            else -> when {
                t < 1_000 -> "RECEIVED" to 0
                t < 3_000 -> "PROCESSING" to 1
                else -> "DONE" to 1
            }
        }
    }

    private fun seedHistory() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        orders += FakeOrder(
            "a1b2c3d4-0000-4000-8000-000000000001", now - 72 * hour,
            listOf(line(4, 10), line(5, 6)), mode = null, fixedStatus = "DONE", fixedAttempts = 1
        )
        orders += FakeOrder(
            "e5f6a7b8-0000-4000-8000-000000000002", now - 26 * hour,
            listOf(line(1, 4), line(2, 3)), mode = null, fixedStatus = "DONE", fixedAttempts = 1
        )
        orders += FakeOrder(
            "c9d0e1f2-0000-4000-8000-000000000003", now - 2 * hour,
            listOf(line(7, 20), line(3, 8)), mode = "POISON", fixedStatus = "FAILED", fixedAttempts = 3
        )
    }

    private fun line(itemId: Int, quantity: Int): OrderLine {
        val item = items.first { it.id == itemId }
        return OrderLine(item.id, item.name, quantity, item.price)
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(ms))

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType()))
    )

    private companion object {
        const val NETWORK_DELAY_MS = 600L
    }
}