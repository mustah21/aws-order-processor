package com.example.aws_g4

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class FriendlyMessageTest {

    private fun http(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType()))
    )

    @Test fun `400 maps to validation message`() {
        assertEquals("The request was not valid.", friendlyMessage(http(400)))
    }

    @Test fun `401 maps to auth message`() {
        assertEquals("Authentication failed. Please sign in again.", friendlyMessage(http(401)))
    }

    @Test fun `429 maps to rate limit message`() {
        assertEquals("Too many requests.", friendlyMessage(http(429)))
    }

    @Test fun `500 maps to server message`() {
        assertEquals("The server is having problems.", friendlyMessage(http(500)))
    }

    @Test fun `IOException maps to network message`() {
        assertEquals("Please check your network connection.", friendlyMessage(IOException()))
    }

    @Test fun `unknown error maps to generic message`() {
        assertEquals("An unknown error occurred.", friendlyMessage(RuntimeException("boom")))
    }
}