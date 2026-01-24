package com.notisync

data class PhoneNotification(
    val id: String,
    val app_package: String,
    val app_name: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val icon: String? = null
)
