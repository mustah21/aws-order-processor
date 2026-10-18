package com.example.aws_g4

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

class ShopRepository(private val api: ShopApi = ShopClient.api) {

    fun logout() {
        AuthManager.signOut()
    }

    suspend fun items(): Result<List<Item>> = safeCall { api.getItems() }

    suspend fun placeOrder(
        orderId: String,
        cart: Map<Int, Int>,
        simulate: SimulateMode
    ): Result<String> = safeCall {
        val request = PlaceOrderRequest(
            orderId = orderId,
            lines = cart.map { (itemId, qty) -> OrderLineRequest(itemId, qty) },
            simulate = simulate.wire
        )
        api.placeOrder(request).orderId
    }

    suspend fun orders(): Result<List<OrderSummary>> = safeCall { api.getOrders() }

    // Coroutine cancellation is rethrown, never swallowed.
    private suspend inline fun <T> safeCall(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}

/** User-facing error text. Internal server messages are never shown as-is. */
fun friendlyMessage(e: Throwable): String = when (e) {
    is HttpException -> when (e.code()) {
        400 -> "The request was not valid."
        401 -> "Authentication failed. Please sign in again."
        429 -> "Too many requests."
        in 500..599 -> "The server is having problems."
        else -> "The request could not be completed (${e.code()})."
    }
    is IOException -> "Please check your network connection."
    else -> "An unknown error occurred."
}