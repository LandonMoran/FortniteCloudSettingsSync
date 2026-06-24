package com.fortnitecloudsync.data.remote

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("account_id") val accountId: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("errorMessage") val errorMessage: String?
)

data class DeviceAuthResponse(
    @SerializedName("accountId") val accountId: String?,
    @SerializedName("deviceId") val deviceId: String?,
    @SerializedName("secret") val secret: String?
)

data class AccountInfoResponse(
    @SerializedName("displayName") val displayName: String?
)
