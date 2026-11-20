package com.example.data.remote.api

import com.example.data.remote.dto.ApiResponseDto
import com.example.data.remote.dto.NotificationDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * [PROPOSED — AWAITING BACKEND CONFIRMATION]
 * Notification feed and read-receipt REST endpoints.
 */
interface NotificationApiService {

    @GET("api/v1/notifications")
    suspend fun getNotifications(): Response<ApiResponseDto<List<NotificationDto>>>

    @PATCH("api/v1/notifications/{notificationId}/read")
    suspend fun markNotificationRead(
        @Path("notificationId") notificationId: Long
    ): Response<Unit>

    @PATCH("api/v1/notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<Unit>
}
