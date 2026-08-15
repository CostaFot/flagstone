package com.costafot.flagstone

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val TEST_KEY = "test-key"

private val testFlags = buildJsonObject {
    put("bool_flag", true)
    put("string_flag", "variant-b")
    put("number_flag", 42)
}

private val testFlagsByApp = mapOf("appone" to testFlags)

class ApplicationTest {

    @Test
    fun `health does not require an api key`() = testApplication {
        application { module(TEST_KEY, testFlagsByApp) }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `flags without api key returns 401`() = testApplication {
        application { module(TEST_KEY, testFlagsByApp) }
        val response = client.get("/v1/flags/appone")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `flags with wrong api key returns 401`() = testApplication {
        application { module(TEST_KEY, testFlagsByApp) }
        val response = client.get("/v1/flags/appone") {
            header("X-API-Key", "wrong-key")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `flags with correct api key returns the app flags and cache header`() = testApplication {
        application { module(TEST_KEY, testFlagsByApp) }
        val response = client.get("/v1/flags/appone") {
            header("X-API-Key", TEST_KEY)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("max-age=300", response.headers["Cache-Control"])
        assertEquals(testFlags, Json.parseToJsonElement(response.bodyAsText()) as JsonObject)
    }

    @Test
    fun `unknown app with correct api key returns 404`() = testApplication {
        application { module(TEST_KEY, testFlagsByApp) }
        val response = client.get("/v1/flags/nope") {
            header("X-API-Key", TEST_KEY)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `loadFlagsDir maps each json file to its app name`() {
        val dir = createTempDirectory("flags").toFile().apply { deleteOnExit() }
        File(dir, "appone.json").writeText("""{"a": true}""")
        File(dir, "apptwo.json").writeText("""{"b": "x", "c": 3}""")
        File(dir, "notes.txt").writeText("ignored")

        val flagsByApp = loadFlagsDir(dir)

        assertEquals(setOf("appone", "apptwo"), flagsByApp.keys)
        assertEquals(2, flagsByApp.getValue("apptwo").size)
    }

    @Test
    fun `loadFlagsDir rejects a missing or empty directory`() {
        assertFailsWith<IllegalArgumentException> { loadFlagsDir(File("does-not-exist")) }
        val empty = createTempDirectory("flags-empty").toFile().apply { deleteOnExit() }
        assertFailsWith<IllegalArgumentException> { loadFlagsDir(empty) }
    }

    @Test
    fun `the real flags directory in this repo is valid`() {
        val flagsByApp = loadFlagsDir(File("flags"))
        assert(flagsByApp.isNotEmpty())
    }

    @Test
    fun `loadFlags rejects non-primitive values`() {
        val file = File.createTempFile("flags", ".json").apply {
            deleteOnExit()
            writeText("""{"nested": {"a": 1}}""")
        }
        assertFailsWith<IllegalArgumentException> { loadFlags(file) }
    }
}
