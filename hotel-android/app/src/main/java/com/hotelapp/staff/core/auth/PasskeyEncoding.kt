package com.hotelapp.staff.core.auth

import java.util.Base64

object PasskeyEncoding {
    fun standardToBase64Url(standardB64: String): String {
        val bytes = Base64.getDecoder().decode(standardB64.trim())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun base64UrlToStandard(base64Url: String): String {
        val bytes = Base64.getUrlDecoder().decode(base64Url.trim())
        return Base64.getEncoder().encodeToString(bytes)
    }
}
