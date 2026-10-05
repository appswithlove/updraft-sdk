package com.appswithlove.updraft.api

import com.appswithlove.updraft.api.response.CheckLastVersionResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json

class CheckLastVersionResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun appWithoutBuilds_decodesWithFlagsFalse() {
        val response = json.decodeFromString<CheckLastVersionResponse>("""{"whats_new":"","version":""}""")
        assertFalse(response.isNewVersion)
        assertFalse(response.isAutoupdateEnabled)
    }
}
