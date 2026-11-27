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
import com.hotelapp.staff.data.dto.PasskeySecondFactorFinishRequestDto
import kotlinx.coroutines.CancellationException
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

class CredentialCancelledException(message: String) : IllegalStateException(message)

data class PasskeyAssertionPayload(
    val credentialId: String,
    val authenticatorData: String,
    val clientDataJson: String,
    val signature: String,
    val challenge: String,
)

class StaffCredentialHelper(context: Context) {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    suspend fun getSavedPassword(
        activityContext: Context,
        expectedServerOrigin: String = "",
    ): Result<SavedPasswordLogin> = try {
        val request = GetCredentialRequest(listOf(GetPasswordOption()))
        val response = credentialManager.getCredential(activityContext, request)
        val cred = response.credential as? PasswordCredential
            ?: return Result.failure(IllegalStateException("Selected credential is not a saved password."))
        val username = extractUsernameForOrigin(cred.id, expectedServerOrigin).getOrElse { err ->
            return Result.failure(err)
        }
        Result.success(SavedPasswordLogin(username = username, password = cred.password))
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: NoCredentialException) {
        Result.failure(IllegalStateException("No saved staff login found on this device."))
    } catch (_: GetCredentialCancellationException) {
        Result.failure(CredentialCancelledException("Sign-in cancelled."))
    } catch (t: Throwable) {
        Result.failure(t)
    }

    suspend fun savePasswordSilently(
        activityContext: Context,
        username: String,
        password: String,
        serverOrigin: String = "",
    ) {
        try {
            credentialManager.createCredential(
                context = activityContext,
                request = CreatePasswordRequest(
                    id = scopedCredentialId(serverOrigin, username),
                    password = password,
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
        }
    }

    suspend fun signInWithPasskey(
        activityContext: Context,
        username: String,
        challengeStandardB64: String,
        allowCredentials: List<PasskeyAllowCredentialDto>,
        rpId: String,
    ): Result<PasskeyLoginFinishRequestDto> =
        authenticatePasskeyAssertion(
            activityContext = activityContext,
            challengeStandardB64 = challengeStandardB64,
            allowCredentials = allowCredentials,
            rpId = rpId,
        ).map { assertion ->
            PasskeyLoginFinishRequestDto(
                username = username.trim(),
                credentialId = assertion.credentialId,
                authenticatorData = assertion.authenticatorData,
                clientDataJson = assertion.clientDataJson,
                signature = assertion.signature,
                challenge = assertion.challenge,
            )
        }

    suspend fun verifySecondFactorWithPasskey(
        activityContext: Context,
        mfaToken: String,
        challengeStandardB64: String,
        allowCredentials: List<PasskeyAllowCredentialDto>,
        rpId: String,
    ): Result<PasskeySecondFactorFinishRequestDto> =
        authenticatePasskeyAssertion(
            activityContext = activityContext,
            challengeStandardB64 = challengeStandardB64,
            allowCredentials = allowCredentials,
            rpId = rpId,
        ).map { assertion ->
            PasskeySecondFactorFinishRequestDto(
                mfaToken = mfaToken,
                credentialId = assertion.credentialId,
                authenticatorData = assertion.authenticatorData,
                clientDataJson = assertion.clientDataJson,
                signature = assertion.signature,
                challenge = assertion.challenge,
            )
        }

    suspend fun authenticatePasskeyAssertion(
        activityContext: Context,
        challengeStandardB64: String,
        allowCredentials: List<PasskeyAllowCredentialDto>,
        rpId: String,
    ): Result<PasskeyAssertionPayload> {
        val cleanRpId = rpId.trim().trim('[', ']')
        if (!isValidWebAuthnRpId(cleanRpId)) {
            return Result.failure(
                IllegalStateException(
                    "Passkey verification requires a domain name (IP addresses such as '$cleanRpId' are not supported by WebAuthn).",
                ),
            )
        }
        return try {
            val challengeUrl = PasskeyEncoding.standardToBase64Url(challengeStandardB64)
            val requestJson = buildJsonObject {
                put("challenge", challengeUrl)
                put("rpId", cleanRpId)
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
                PasskeyAssertionPayload(
                    credentialId = credentialId,
                    authenticatorData = PasskeyEncoding.base64UrlToStandard(authDataUrl),
                    clientDataJson = PasskeyEncoding.base64UrlToStandard(clientDataUrl),
                    signature = PasskeyEncoding.base64UrlToStandard(signatureUrl),
                    challenge = challengeStandardB64,
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: NoCredentialException) {
            Result.failure(IllegalStateException("No passkey found on this device for $cleanRpId."))
        } catch (_: GetCredentialCancellationException) {
            Result.failure(CredentialCancelledException("Passkey prompt cancelled."))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    companion object {
        private val IPV4_REGEX = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        fun scopedCredentialId(serverOrigin: String, username: String): String {
            val cleanUser = username.trim()
            val normalizedOrigin = SessionManager.normalizeOrigin(serverOrigin.trim()) ?: return cleanUser
            return "$normalizedOrigin|$cleanUser"
        }

        fun extractUsernameForOrigin(rawId: String, expectedServerOrigin: String): Result<String> {
            val trimmed = rawId.trim()
            val separatorIndex = trimmed.lastIndexOf('|')
            if (separatorIndex < 0) {
                return Result.success(trimmed)
            }
            val credentialOrigin = trimmed.substring(0, separatorIndex)
            val username = trimmed.substring(separatorIndex + 1).trim()
            val expectedOrigin = SessionManager.normalizeOrigin(expectedServerOrigin.trim())
            if (expectedOrigin == null || credentialOrigin != expectedOrigin || username.isEmpty()) {
                return Result.failure(
                    IllegalStateException("Saved credential belongs to a different hotel server."),
                )
            }
            return Result.success(username)
        }

        internal fun isValidWebAuthnRpId(rpId: String): Boolean {
            if (rpId.isBlank()) return false
            if (IPV4_REGEX.matches(rpId) || ':' in rpId) return false
            return true
        }
    }
}
