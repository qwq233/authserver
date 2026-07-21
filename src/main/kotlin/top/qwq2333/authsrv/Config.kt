package top.qwq2333.authsrv

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.readText

@Serializable
internal data class DatabaseConfig(
    val ip: String,
    val port: Int,
    val username: String,
    val password: String,
    val database: String = "qn_auth",
    val maximumPoolSize: Int = 10,
)

internal fun loadDatabaseConfig(path: Path = Path.of("config.json")): DatabaseConfig {
    val config = apiJson.decodeFromString<DatabaseConfig>(path.readText())
    require(config.ip.isNotBlank()) { "config.json: ip must not be blank" }
    require(config.port in 1..65535) { "config.json: port must be between 1 and 65535" }
    require(config.database.matches(Regex("[A-Za-z0-9_]+"))) {
        "config.json: database must contain only letters, digits, and underscores"
    }
    require(config.maximumPoolSize in 1..64) {
        "config.json: maximumPoolSize must be between 1 and 64"
    }
    return config
}

internal fun createDataSource(config: DatabaseConfig): HikariDataSource =
    HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:mariadb://${config.ip}:${config.port}/${config.database}"
            username = config.username
            password = config.password
            poolName = "authserver"
            maximumPoolSize = config.maximumPoolSize
            connectionTimeout = 10_000
            validationTimeout = 5_000
        },
    )
