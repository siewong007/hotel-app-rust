package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.AuthResponseDto
import com.hotelapp.staff.data.dto.PasskeyLoginFinishRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartRequestDto
import com.hotelapp.staff.data.dto.PasskeyLoginStartResponseDto
import com.hotelapp.staff.data.dto.PasskeySecondFactorFinishRequestDto
import com.hotelapp.staff.data.dto.PasskeySecondFactorStartRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

interface PasskeyApi {
    @POST("/api/auth/passkey/login/start")
    suspend fun loginStart(@Body body: PasskeyLoginStartRequestDto): PasskeyLoginStartResponseDto

    @POST("/api/auth/passkey/login/finish")
    suspend fun loginFinish(@Body body: PasskeyLoginFinishRequestDto): AuthResponseDto

    @POST("/api/auth/passkey/2fa/start")
    suspend fun secondFactorStart(@Body body: PasskeySecondFactorStartRequestDto): PasskeyLoginStartResponseDto

    @POST("/api/auth/passkey/2fa/finish")
    suspend fun secondFactorFinish(@Body body: PasskeySecondFactorFinishRequestDto): AuthResponseDto
}
