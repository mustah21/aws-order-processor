package com.example.aws_g4

// ---- Authentication ----
// Sign-in is handled by Microsoft Entra ID (see AuthManager). The app only keeps who is signed in;
// tokens are held and refreshed by MSAL.
data class Session(val username: String)

// ---- Items ----
data class Item(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val price: Double
)

// ---- Placing an order ----
// customerId is intentionally not sent. The server reads it from the token
// (a client-supplied user ID must never be trusted).
data class OrderLineRequest(val itemId: Int, val quantity: Int)

data class PlaceOrderRequest(
    val orderId: String,                 // UUID created by the app (idempotency key)
    val lines: List<OrderLineRequest>,
    val simulate: String? = null         // demo only: "TRANSIENT" / "POISON" / null
)

data class PlaceOrderResponse(val orderId: String)

// ---- Order status (polled after placing an order) ----
data class OrderLine(
    val itemId: Int,
    val name: String,
    val quantity: Int,
    val unitPrice: Double
)

/** status: RECEIVED / PROCESSING / DONE / FAILED (FAILED = the order ended up in the DLQ) */
data class OrderSummary(
    val orderId: String,
    val status: String,
    val attempts: Int,
    val createdAt: String,
    val total: Double,
    val lines: List<OrderLine>
)

/** Developer option: failure simulation mode (remove from production builds). */
enum class SimulateMode(val label: String, val wire: String?) {
    NONE("Process normally", null),
    TRANSIENT("Temporary failure (succeeds after retries)", "TRANSIENT"),
    POISON("Permanent failure (moves to DLQ)", "POISON")
}