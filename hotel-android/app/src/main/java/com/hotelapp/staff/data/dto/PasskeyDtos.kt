package com.hotelapp.staff.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PasskeyLoginStartRequestDto(
    val username: String,
)

@Serializable
data class PasskeyAllowCredentialDto(
    val id: String,
    val type: String = "public-key",
)

@Serializable
data class PasskeyLoginStartResponseDto(
    val challenge: String,
    val allowCredentials: List<PasskeyAllowCredentialDto> = emptyList(),
)

@Serializable
data class PasskeyLoginFinishRequestDto(
    @SerialName("credential_id") val credentialId: String,
    @SerialName("authenticator_data") val authenticatorData: String,
    @SerialName("client_data_json") val clientDataJson: String,
    val signature: String,
    val challenge: String,
)
