package top.qwq2333.authsrv

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import java.util.Date
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Instant

fun Application.installAuthServer(dataSource: DataSource, startedAt: Instant) {
    val logger = LoggerFactory.getLogger("AuthServer")
    val service = AuthService(initializeDatabase(dataSource))
    val counters = RequestCounters()

    install(ContentNegotiation) {
        json(apiJson)
    }
    install(CallLogging)
    install(StatusPages) {
        exception<RequestException> { call, error ->
            call.respondApi(BasicResponse(400, error.responseReason), counters)
        }
        exception<Throwable> { call, error ->
            logger.error("Unhandled request failure", error)
            call.respondApi(unknownError(), counters)
        }
    }

    routing {
        get("/") { call.respondHealth(startedAt, counters) }
        get("/qa") { call.respondHealth(startedAt, counters) }
        installApiRoutes(service, counters)
        route("/qa") {
            installApiRoutes(service, counters)
        }
    }
}

private fun Route.installApiRoutes(service: AuthService, counters: RequestCounters) {
    post("user") {
        call.handle(counters) { request ->
            service.updateUser(
                uin = request.requiredLong("uin"),
                status = request.intOrZero("status"),
                token = request.requiredString("token"),
                reason = request.requiredString("reason"),
            )
        }
    }
    post("user/query") {
        call.handle(counters) { request ->
            service.queryUser(request.requiredLong("uin"))
        }
    }
    delete("user") {
        call.handle(counters) { request ->
            service.deleteUser(
                uin = request.requiredLong("uin"),
                token = request.requiredString("token"),
                reason = request.requiredString("reason"),
            )
        }
    }
    post("admin") {
        call.handle(counters) { request ->
            service.promoteAdmin(
                destinationToken = request.requiredString("desttoken"),
                nickname = request.requiredString("nickname"),
                reason = request.optionalString("reason"),
                token = request.requiredString("token"),
            )
        }
    }
    delete("admin") {
        call.handle(counters) { request ->
            service.revokeAdmin(
                destinationToken = request.requiredString("desttoken"),
                token = request.requiredString("token"),
            )
        }
    }
    get("user/history") {
        call.handle(counters) { request ->
            service.queryHistory(
                uin = request.requiredLong("uin"),
                token = request.requiredString("token"),
            )
        }
    }
    post("statistics/card/send") {
        call.handle(counters) { request ->
            service.sendCard(
                uin = request.requiredLong("uin"),
                message = request.requiredString("msg"),
            )
        }
    }
}

private suspend fun ApplicationCall.handle(
    counters: RequestCounters,
    block: suspend (kotlinx.serialization.json.JsonObject) -> ApiResponse,
) {
    respondApi(block(parseRequest(receiveText())), counters)
}

private suspend fun ApplicationCall.respondApi(response: ApiResponse, counters: RequestCounters) {
    counters.record(response.code)
    val status = HttpStatusCode.fromValue(response.code)
    when (response) {
        is BasicResponse -> respond(status = status, message = response)
        is UserResponse -> respond(status = status, message = response)
        is HistoryResponse -> respond(status = status, message = response)
    }
}

private suspend fun ApplicationCall.respondHealth(startedAt: Instant, counters: RequestCounters) {
    val uptime = (Clock.System.now() - startedAt).inWholeSeconds
    respondText(
        text = "Server started at ${Date(startedAt.toEpochMilliseconds())}, running for ${uptime}s\n" +
            "requestCount = ${counters.requestCount} \nError Count${counters.errorCount}",
        contentType = ContentType("application", "text"),
    )
}
