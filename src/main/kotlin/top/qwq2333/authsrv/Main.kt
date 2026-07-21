package top.qwq2333.authsrv

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import javax.sql.DataSource
import kotlin.time.Clock
import kotlin.time.Instant

private const val SERVER_PORT = 10810
private const val STOP_GRACE_MILLIS = 1_000L
private const val STOP_TIMEOUT_MILLIS = 5_000L

fun main() {
    val startedAt = Clock.System.now()
    createDataSource(loadDatabaseConfig()).use { dataSource ->
        val service = AuthService(initializeDatabase(dataSource))
        authServer(service, SERVER_PORT, startedAt).start(wait = true)
    }
}

internal fun startAuthServer(
    dataSource: DataSource,
    port: Int,
): AutoCloseable {
    val service = AuthService(initializeDatabase(dataSource))
    return startAuthServer(service, port, Clock.System.now())
}

private fun startAuthServer(
    service: AuthService,
    port: Int,
    startedAt: Instant,
): AutoCloseable {
    val server = authServer(service, port, startedAt)
    server.start(wait = false)
    return AutoCloseable { server.stop(STOP_GRACE_MILLIS, STOP_TIMEOUT_MILLIS) }
}

private fun authServer(service: AuthService, port: Int, startedAt: Instant) =
    embeddedServer(CIO, port = port) {
        installAuthServer(service, startedAt)
    }
