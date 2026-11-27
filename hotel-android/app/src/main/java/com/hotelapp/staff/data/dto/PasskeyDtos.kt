package com.hotelapp.staff.data.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PasskeyLoginStartRequestDto(
    val username: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PasskeyAllowCredentialDto(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val type: String = "public-key",
)

@Serializable
data class PasskeyLoginStartResponseDto(
    val challenge: String,
    val allowCredentials: List<PasskeyAllowCredentialDto> = emptyList(),
)

@Serializable
data class PasskeyLoginFinishRequestDto(
    val username: String,
    @SerialName("credential_id") val credentialId: String,
    @SerialName("authenticator_data") val authenticatorData: String,
    @SerialName("client_data_json") val clientDataJson: String,
    val signature: String,
    val challenge: String,
)

@Serializable
data class PasskeySecondFactorStartRequestDto(
    @SerialName("mfa_token") val mfaToken: String,
)

@Serializable
data class PasskeySecondFactorFinishRequestDto(
    @SerialName("mfa_token") val mfaToken: String,
    @SerialName("credential_id") val credentialId: String,
    @SerialName("authenticator_data") val authenticatorData: String,
    @SerialName("client_data_json") val clientDataJson: String,
    val signature: String,
    val challenge: String,
)
