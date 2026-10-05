package com.appswithlove.updraft.api.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckLastVersionResponse(
    @SerialName("create_at") val createAt: String? = null,
    @SerialName("whats_new") val whatsNew: String? = null,
    @SerialName("version") val version: String? = null,
    @SerialName("your_version") val yourVersion: String? = null,
    @SerialName("is_new_version") val isNewVersion: Boolean = false,
    @SerialName("is_autoupdate_enabled") val isAutoupdateEnabled: Boolean = false,
)
