package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.AuthResponseDto
import com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface PasskeyApi {
    @POST("/api/auth/passkey/login/start")
    suspend fun loginStart(@Body body: PasskeyLoginStartRequestDto): PasskeyLoginStartResponseDto

    @POST("/api/auth/passkey/login/finish")
    suspend fun loginFinish(@Body body: PasskeyLoginFinishRequestDto): AuthResponseDto
}
