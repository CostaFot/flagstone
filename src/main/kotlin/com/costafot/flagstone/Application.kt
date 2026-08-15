package com.costafot.flagstone

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.security.MessageDigest

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val apiKey = System.getenv("FLAGSTONE_API_KEY")
        ?: error("FLAGSTONE_API_KEY environment variable is not set")
    val flagsDir = System.getenv("FLAGS_DIR") ?: "flags"
    val flagsByApp = loadFlagsDir(File(flagsDir))

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module(apiKey, flagsByApp)
    }.start(wait = true)
}

fun loadFlagsDir(dir: File): Map<String, JsonObject> {
    require(dir.isDirectory) { "Flags directory not found: ${dir.absolutePath}" }
    val files = dir.listFiles { file -> file.isFile && file.extension == "json" }.orEmpty()
    require(files.isNotEmpty()) { "No .json flag files in ${dir.absolutePath}" }
    return files.associate { it.nameWithoutExtension to loadFlags(it) }
}

fun loadFlags(file: File): JsonObject {
    require(file.exists()) { "Flags file not found: ${file.absolutePath}" }
    val flags = Json.parseToJsonElement(file.readText()) as? JsonObject
        ?: throw IllegalArgumentException("Flags file ${file.name} must contain a JSON object")
    for ((name, value) in flags) {
        require(value is JsonPrimitive) {
            "Flag \"$name\" in ${file.name} must be a primitive (boolean, string, or number), was: $value"
        }
    }
    return flags
}

fun Application.module(apiKey: String, flagsByApp: Map<String, JsonObject>) {
    install(ContentNegotiation) {
        json()
    }
    // Everything requires the API key except /health; unknown paths fail closed.
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.path() == "/health") return@intercept
        val provided = call.request.headers["X-API-Key"]
        if (provided == null || !constantTimeEquals(provided, apiKey)) {
            call.respond(HttpStatusCode.Unauthorized)
            finish()
        }
    }
    routing {
        get("/health") {
            call.respondText("OK")
        }
        get("/v1/flags/{app}") {
            val flags = flagsByApp[call.parameters["app"]]
            if (flags == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.response.headers.append(HttpHeaders.CacheControl, "max-age=300")
                call.respond(flags)
            }
        }
    }
}

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
