package com.example.aws_g4


import android.util.Base64
import org.json.JSONObject

data class AzureUserInfo(
    val userId: String?,
    val email: String?
)

object JwtUtils {
    fun parseAzureToken(accessToken: String): AzureUserInfo? {
        return try {
            val parts = accessToken.split(".")
            if (parts.size < 2) return null

            val payloadJson = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val jsonObject = JSONObject(payloadJson)

            val userId = jsonObject.optString("oid", jsonObject.optString("sub", null))
            val email = jsonObject.optString("email", jsonObject.optString("upn", jsonObject.optString("preferred_username", null)))

            AzureUserInfo(userId, email)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}