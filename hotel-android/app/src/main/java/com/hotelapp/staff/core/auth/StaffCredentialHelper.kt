package com.hotelapp.staff.core.auth

import android.content.Context
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.hotelapp.staff.data.dto.PasskeyAllowCredentialDto
import com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class SavedPasswordLogin(
    val username: String,
    val password: String,
)

class StaffCredentialHelper(context: Context) {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    suspend fun getSavedPassword(activityContext: Context): Result<SavedPasswordLogin> = try {
        val request = GetCredentialRequest(listOf(GetPasswordOption()))
        val response = credentialManager.getCredential(activityContext, request)
        val cred = response.credential as? PasswordCredential
            ?: return Result.failure(IllegalStateException("Selected credential is not a saved password."))
        Result.success(SavedPasswordLogin(username = cred.id, password = cred.password))
    } catch (_: NoCredentialException) {
        Result.failure(IllegalStateException("No saved staff login found on this device."))
    } catch (_: GetCredentialCancellationException) {
        Result.failure(IllegalStateException("Sign-in cancelled."))
    } catch (t: Throwable) {
        Result.failure(t)
    }

    suspend fun savePasswordSilently(
        activityContext: Context,
        username: String,
        password: String,
    ) {
        runCatching {
            credentialManager.createCredential(
                context = activityContext,
                request = CreatePasswordRequest(id = username.trim(), password = password),
            )
        }
    }

    suspend fun signInWithPasskey(
        activityContext: Context,
        challengeStandardB64: String,
        allowCredentials: List<PasskeyAllowCredentialDto>,
        rpId: String,
    ): Result<PasskeyLoginFinishRequestDto> = try {
        val challengeUrl = PasskeyEncoding.standardToBase64Url(challengeStandardB64)
        val requestJson = buildJsonObject {
            put("challenge", challengeUrl)
            put("rpId", rpId)
            put("timeout", 60_000)
            put("userVerification", "preferred")
            put(
                "allowCredentials",
                JsonArray(
                    allowCredentials.map { cred ->
                        buildJsonObject {
                            put("id", cred.id)
                            put("type", cred.type)
                        }
                    },
                ),
            )
        }.toString()

        val response = credentialManager.getCredential(
            context = activityContext,
            request = GetCredentialRequest(listOf(GetPublicKeyCredentialOption(requestJson))),
        )
        val pk = response.credential as? PublicKeyCredential
            ?: return Result.failure(IllegalStateException("Selected credential is not a passkey."))
        val root = Json.parseToJsonElement(pk.authenticationResponseJson).jsonObject
        val assertion = root["response"]?.jsonObject
            ?: return Result.failure(IllegalStateException("Passkey assertion response is missing response payload."))
        val credentialId = root["id"]?.jsonPrimitive?.content
            ?: (root["rawId"] as? JsonPrimitive)?.content
            ?: return Result.failure(IllegalStateException("Passkey assertion response is missing credential ID."))

        val authDataUrl = assertion["authenticatorData"]?.jsonPrimitive?.content
            ?: return Result.failure(IllegalStateException("Missing authenticatorData."))
        val clientDataUrl = assertion["clientDataJSON"]?.jsonPrimitive?.content
            ?: return Result.failure(IllegalStateException("Missing clientDataJSON."))
        val signatureUrl = assertion["signature"]?.jsonPrimitive?.content
            ?: return Result.failure(IllegalStateException("Missing signature."))

        Result.success(
            PasskeyLoginFinishRequestDto(
                credentialId = credentialId,
                authenticatorData = PasskeyEncoding.base64UrlToStandard(authDataUrl),
                clientDataJson = PasskeyEncoding.base64UrlToStandard(clientDataUrl),
                signature = PasskeyEncoding.base64UrlToStandard(signatureUrl),
                challenge = challengeStandardB64,
            ),
        )
    } catch (_: NoCredentialException) {
        Result.failure(IllegalStateException("No passkey found on this device for $rpId."))
    } catch (_: GetCredentialCancellationException) {
        Result.failure(IllegalStateException("Passkey prompt cancelled."))
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
