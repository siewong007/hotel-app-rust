package com.hotelapp.staff.data.api

import com.hotelapp.staff.data.dto.AccessSnapshotDto
import com.hotelapp.staff.data.dto.AuthResponseDto
import com.hotelapp.staff.data.dto.LoginRequestDto
import com.hotelapp.staff.data.dto.MessageResponseDto
import com.hotelapp.staff.data.dto.RefreshTokenResponseDto
import com.hotelapp.staff.data.dto.UserProfileDto
import com.hotelapp.staff.data.dto.UserSessionInfoDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthApi {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    @POST("/api/auth/refresh")
    suspend fun refresh(): RefreshTokenResponseDto

    @POST("/api/auth/logout")
    suspend fun logout(): MessageResponseDto

    @GET("/api/auth/access")
    suspend fun accessSnapshot(): AccessSnapshotDto

    @GET("/api/profile")
    suspend fun profile(): UserProfileDto

    @GET("/api/profile/sessions")
    suspend fun sessions(): List<UserSessionInfoDto>

    @DELETE("/api/profile/sessions/{id}")
    suspend fun revokeSession(@Path("id") id: String): MessageResponseDto
}
