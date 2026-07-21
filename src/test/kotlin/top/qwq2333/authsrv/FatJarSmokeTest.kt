package top.qwq2333.authsrv

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.regex.Pattern

private const val USER_AT_ROOT = """{"uin":"90001","status":3,"token":"test_root","reason":"root"}"""
private const val USER_AT_QA = """{"uin":90001,"status":"9","token":"test_root","reason":"qa"}"""
private const val QUERY_USER = """{"uin":90001}"""
private const val HISTORY_REQUEST = """{"uin":90001,"token":"test_root"}"""

class FatJarSmokeTest {
    @Test
    fun optimizedJarRunsAgainstTestOnlyH2() {
        Class.forName(
            "org.fusesource.jansi.internal.CLibrary",
            false,
            javaClass.classLoader,
        ).getDeclaredField("HAVE_ISATTY")

        val socket = ServerSocket(0)
        val port = socket.localPort
        socket.close()

        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:fatjar_" + UUID.randomUUID() +
                    ";MODE=MariaDB;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
                username = "sa"
                password = ""
                maximumPoolSize = 2
            },
        )
        val server = startAuthServer(dataSource, port)
        try {
            val client = requireValue(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build(),
            )

            assertEquals(200, send(client, port, "/", "GET", null).statusCode())
            assertEquals(200, send(client, port, "/qa", "GET", null).statusCode())

            assertCode(send(client, port, "/user", "POST", USER_AT_ROOT), 200)
            assertUser(send(client, port, "/qa/user/query", "POST", QUERY_USER), 3, "root")

            assertCode(send(client, port, "/qa/user", "POST", USER_AT_QA), 200)
            assertUser(send(client, port, "/user/query", "POST", QUERY_USER), 9, "qa")

            val history = send(client, port, "/qa/user/history", "GET", HISTORY_REQUEST)
            assertCode(history, 200)
            assertEquals(2L, Pattern.compile("\"id\"\\s*:").matcher(history.body()).results().count())
        } finally {
            server.close()
            dataSource.close()
        }
    }

    private fun send(
        client: HttpClient,
        port: Int,
        path: String,
        method: String,
        body: String?,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(10))
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody())
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body))
        }
        return requireValue(client.send(builder.build(), HttpResponse.BodyHandlers.ofString()))
    }

    private fun assertCode(response: HttpResponse<String>, expected: Int) {
        assertEquals(expected, response.statusCode())
        assertEquals(expected, intField(requireValue(response.body()), "code"))
        val contentType = requireValue(response.headers().firstValue("Content-Type").orElse(""))
        assertTrue(Pattern.compile("^application/json").matcher(contentType).find())
    }

    private fun assertUser(response: HttpResponse<String>, status: Int, reason: String) {
        assertCode(response, 200)
        val body = requireValue(response.body())
        assertEquals(status, intField(body, "status"))
        assertEquals(reason, stringField(body, "reason"))
    }

    private fun intField(json: String, name: String): Int {
        val matcher = Pattern.compile("\"$name\"\\s*:\\s*(-?\\d+)").matcher(json)
        if (!matcher.find()) throw AssertionError("Missing integer field '$name' in $json")
        return requireValue(matcher.group(1)).toInt()
    }

    private fun stringField(json: String, name: String): String {
        val matcher = Pattern.compile("\"$name\"\\s*:\\s*\"([^\"]*)\"").matcher(json)
        if (!matcher.find()) throw AssertionError("Missing string field '$name' in $json")
        return requireValue(matcher.group(1))
    }

    private fun <T : Any> requireValue(value: T?): T {
        if (value == null) throw AssertionError("Unexpected null from Java API")
        return value
    }
}
