package top.qwq2333.authsrv

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicLong

internal val apiJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal const val EMPTY_REQUEST_REASON = "Request body is empty."
internal const val INVALID_REQUEST_REASON = "Request Body should be a valid JSON object."
internal const val AUTHENTICATION_FAILED_REASON = "Authentication failed"
internal const val FORBIDDEN_REASON = "You don't have permissions to do that"
internal const val DATABASE_ERROR_REASON = "Can't connect to the database.please try again later."
internal const val UNKNOWN_ERROR_REASON = "Unknown error"
internal const val ADMIN_EXISTS_REASON = "dest admin token exist"
internal const val ADMIN_MISSING_REASON = "dest admin token is not exist"

internal val tokenPattern = Regex("^[0-9A-Za-z_]+$")

internal sealed interface ApiResponse {
    val code: Int
}

@Serializable
internal data class BasicResponse(
    override val code: Int,
    val reason: String,
) : ApiResponse

@Serializable
internal data class UserResponse(
    override val code: Int,
    val status: Int,
    val reason: String,
    val lastUpdate: String,
) : ApiResponse

@Serializable
internal data class HistoryResponse(
    override val code: Int,
    val history: List<HistoryEntry>,
) : ApiResponse

@Serializable
internal data class HistoryEntry(
    val id: String,
    val uin: String,
    val operator: String,
    val operation: String,
    val changes: String,
    val reason: String,
    val date: String,
)

internal fun successResponse() = BasicResponse(200, "")
internal fun authenticationFailed() = BasicResponse(401, AUTHENTICATION_FAILED_REASON)
internal fun forbidden() = BasicResponse(403, FORBIDDEN_REASON)
internal fun databaseError() = BasicResponse(500, DATABASE_ERROR_REASON)
internal fun unknownError() = BasicResponse(500, UNKNOWN_ERROR_REASON)

internal sealed class RequestException(val responseReason: String) : RuntimeException(responseReason)
internal class EmptyRequestException : RequestException(EMPTY_REQUEST_REASON)
internal class InvalidRequestException : RequestException(INVALID_REQUEST_REASON)

internal fun parseRequest(body: String): JsonObject {
    if (body.isBlank()) throw EmptyRequestException()
    return try {
        apiJson.decodeFromString<JsonObject>(body)
    } catch (_: SerializationException) {
        throw InvalidRequestException()
    } catch (_: IllegalArgumentException) {
        throw InvalidRequestException()
    }
}

internal fun JsonObject.requiredString(name: String): String {
    val value = this[name] ?: throw EmptyRequestException()
    if (value === JsonNull) throw EmptyRequestException()
    val primitive = value as? JsonPrimitive ?: throw InvalidRequestException()
    if (!primitive.isString) throw InvalidRequestException()
    return primitive.content
}

internal fun JsonObject.optionalString(name: String): String? {
    val value = this[name] ?: return null
    if (value === JsonNull) return null
    val primitive = value as? JsonPrimitive ?: throw InvalidRequestException()
    if (!primitive.isString) throw InvalidRequestException()
    return primitive.content
}

internal fun JsonObject.requiredLong(name: String): Long {
    val value = this[name] ?: throw EmptyRequestException()
    if (value === JsonNull) throw EmptyRequestException()
    val primitive = value as? JsonPrimitive ?: throw InvalidRequestException()
    return primitive.content.toLongOrNull() ?: throw InvalidRequestException()
}

internal fun JsonObject.intOrZero(name: String): Int {
    val value = this[name] ?: return 0
    if (value === JsonNull) return 0
    val primitive = value as? JsonPrimitive ?: throw InvalidRequestException()
    return primitive.content.toIntOrNull() ?: throw InvalidRequestException()
}

internal class RequestCounters {
    private val requests = AtomicLong()
    private val errors = AtomicLong()

    val requestCount: Long get() = requests.get()
    val errorCount: Long get() = errors.get()

    fun record(code: Int) {
        requests.bump()
        if (code >= 500) errors.bump()
    }

    private fun AtomicLong.bump() {
        updateAndGet { current -> if (current >= 1_000_000_000L) 1L else current + 1L }
    }
}
