package com.example.aws_g4

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ShopApi {
    @GET("items")
    suspend fun getItems(): List<Item>

    /** Returns 202 as soon as the order is queued. Processing happens asynchronously (SQS -> Lambda). */
    @POST("orders")
    suspend fun placeOrder(@Body body: PlaceOrderRequest): PlaceOrderResponse

    /** Order history of the signed-in customer. The server must only return the caller's own orders. */
    @GET("orders")
    suspend fun getOrders(): List<OrderSummary>
}

object ShopClient {
    // true  = use the in-memory FakeShopApi (no backend needed, for UI work)
    // false = call the real API Gateway at BASE_URL
    private val USE_FAKE_API = false

    // TODO: replace with the real Invoke URL from API Gateway (the trailing / is required).
    // Stage "$default" -> https://<id>.execute-api.<region>.amazonaws.com/
    // Stage "prod"     -> https://<id>.execute-api.<region>.amazonaws.com/prod/
    private const val BASE_URL = "https://hbi9etk2qk.execute-api.us-east-1.amazonaws.com/prod/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()

            AuthManager.currentToken()?.let { token ->
             //Auth header added
                builder.addHeader("Authorization", "Bearer $token")

                val userInfo = JwtUtils.parseAzureToken(token)
                userInfo?.let { info ->
                    info.userId?.let { builder.addHeader("X-User-Id", it) }
                    info.email?.let { builder.addHeader("X-User-Email", it) }
                }
            }

            chain.proceed(builder.build())
        }
        .build()

    val api: ShopApi by lazy {
        if (USE_FAKE_API) {
            FakeShopApi()
        } else {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ShopApi::class.java)
        }
    }
}