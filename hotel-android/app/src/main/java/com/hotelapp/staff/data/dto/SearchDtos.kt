package com.hotelapp.staff.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchResultItemDto(
    val id: Long,
    val title: String,
    val subtitle: String,
    val route: String,
)

@Serializable
data class SearchGroupDto(
    val type: String,
    val label: String,
    val results: List<SearchResultItemDto> = emptyList(),
)

@Serializable
data class GlobalSearchResponseDto(
    val query: String = "",
    val groups: List<SearchGroupDto> = emptyList(),
)
