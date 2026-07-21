package top.qwq2333.authsrv

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class AuthServerTest {
    @Test
    fun userContractUnderBothPrefixes() {
        listOf("", "/qa").forEach { prefix ->
            h2DataSource().use { dataSource ->
                val service = AuthService(initializeDatabase(dataSource))
                testApplication {
                    application { installAuthServer(service, Clock.System.now()) }

                    val health = client.get(if (prefix.isEmpty()) "/" else "/qa")
                    assertEquals(HttpStatusCode.OK, health.status)
                    assertTrue(health.bodyAsText().contains("requestCount ="))

                    assertCode(
                        client.post("$prefix/user") {
                            setBody("""{"uin":"10001","token":"test_root","reason":"first","ignored":true}""")
                        },
                        200,
                    )
                    assertUser(client.post("$prefix/user/query") {
                        setBody("""{"uin":10001}""")
                    }, status = 0, reason = "first")

                    assertCode(
                        client.post("$prefix/user") {
                            setBody("""{"uin":10001,"status":"7","token":"test_root","reason":"second"}""")
                        },
                        200,
                    )
                    assertUser(client.post("$prefix/user/query") {
                        setBody("""{"uin":"10001"}""")
                    }, status = 7, reason = "second")

                    val history = client.get("$prefix/user/history") {
                        setBody("""{"uin":"10001","token":"test_root"}""")
                    }
                    assertEquals(HttpStatusCode.OK, history.status)
                    val entries = history.json()["history"]!!.jsonArray
                    assertEquals(2, entries.size)
                    assertEquals("null -> 0", entries[0].jsonObject["changes"]!!.jsonPrimitive.content)
                    assertEquals("0 -> 7", entries[1].jsonObject["changes"]!!.jsonPrimitive.content)
                    entries.forEach { entry ->
                        entry.jsonObject.values.forEach { value ->
                            assertTrue(value is JsonPrimitive && value.isString)
                        }
                        assertTrue(entry.jsonObject["date"]!!.jsonPrimitive.content.matches(DATE_TIME))
                    }

                    assertCode(client.delete("$prefix/user") {
                        setBody("""{"uin":10001,"token":"test_root","reason":"gone"}""")
                    }, 200)
                    val historyAfterDelete = client.get("$prefix/user/history") {
                        setBody("""{"uin":10001,"token":"test_root"}""")
                    }.json()["history"]!!.jsonArray
                    assertEquals(3, historyAfterDelete.size)

                    assertCode(client.delete("$prefix/user") {
                        setBody("""{"uin":10001,"token":"test_root","reason":"again"}""")
                    }, 200)
                    val historyAfterSecondDelete = client.get("$prefix/user/history") {
                        setBody("""{"uin":10001,"token":"test_root"}""")
                    }.json()["history"]!!.jsonArray
                    assertEquals(3, historyAfterSecondDelete.size)
                    assertUser(client.post("$prefix/user/query") {
                        setBody("""{"uin":10001}""")
                    }, status = 0, reason = "", lastUpdate = "")
                }
            }
        }
    }

    @Test
    fun adminRolesAndCardsUnderBothPrefixes() {
        listOf("", "/qa").forEach { prefix ->
            h2DataSource().use { dataSource ->
                val service = AuthService(initializeDatabase(dataSource))
                testApplication {
                    application { installAuthServer(service, Clock.System.now()) }

                    assertCode(client.post("$prefix/admin") {
                        setBody("""{"desttoken":"child","nickname":"Child","token":"test_root"}""")
                    }, 200)
                    assertCode(client.post("$prefix/admin") {
                        setBody("""{"desttoken":"grand","nickname":"Grand","token":"child","reason":"r"}""")
                    }, 200)

                    val duplicate = client.post("$prefix/admin") {
                        setBody("""{"desttoken":"child","nickname":"Again","token":"test_root"}""")
                    }
                    assertEquals(403, duplicate.json()["code"]!!.jsonPrimitive.int)
                    assertEquals(ADMIN_EXISTS_REASON, duplicate.json()["reason"]!!.jsonPrimitive.content)

                    assertCode(client.delete("$prefix/admin") {
                        setBody("""{"desttoken":"test_root","token":"child"}""")
                    }, 403)
                    assertCode(client.delete("$prefix/admin") {
                        setBody("""{"desttoken":"grand","token":"child"}""")
                    }, 200)
                    assertCode(client.delete("$prefix/admin") {
                        setBody("""{"desttoken":"child","token":"test_root"}""")
                    }, 200)

                    val missing = client.delete("$prefix/admin") {
                        setBody("""{"desttoken":"missing","token":"test_root"}""")
                    }
                    assertEquals(ADMIN_MISSING_REASON, missing.json()["reason"]!!.jsonPrimitive.content)

                    assertCode(client.post("$prefix/statistics/card/send") {
                        setBody("""{"uin":"10001","msg":"card-data"}""")
                    }, 200)
                    transaction(database(dataSource)) {
                        val card = Cards.selectAll().single()
                        assertEquals("card-data", card[Cards.cardMsg])
                        assertEquals("sendCard", card[Cards.type])
                    }
                }
            }
        }
    }

    @Test
    fun invalidBodiesReturnMatching400() {
        h2DataSource().use { dataSource ->
            val service = AuthService(initializeDatabase(dataSource))
            testApplication {
                application { installAuthServer(service, Clock.System.now()) }
                listOf(
                    "" to EMPTY_REQUEST_REASON,
                    "{" to INVALID_REQUEST_REASON,
                    "[]" to INVALID_REQUEST_REASON,
                    """{"uin":"9223372036854775808"}""" to INVALID_REQUEST_REASON,
                ).forEach { (body, reason) ->
                    val response = client.post("/user/query") { setBody(body) }
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                    assertEquals(400, response.json()["code"]!!.jsonPrimitive.int)
                    assertEquals(reason, response.json()["reason"]!!.jsonPrimitive.content)
                }
            }
        }
    }

    @Test
    fun failedLogWriteRollsBackUserMutation() {
        h2DataSource().use { dataSource ->
            val database = initializeDatabase(dataSource)
            val service = AuthService(database)
            testApplication {
                application { installAuthServer(service, Clock.System.now()) }
                assertCode(client.post("/user") {
                    setBody("""{"uin":10001,"status":1,"token":"test_root","reason":"first"}""")
                }, 200)
                transaction(database) { SchemaUtils.drop(History) }

                assertCode(client.post("/user") {
                    setBody("""{"uin":10001,"status":9,"token":"test_root","reason":"broken"}""")
                }, 500)
                assertUser(client.post("/user/query") {
                    setBody("""{"uin":10001}""")
                }, status = 1, reason = "first")

                assertCode(client.delete("/user") {
                    setBody("""{"uin":10001,"token":"test_root","reason":"broken"}""")
                }, 500)
                assertUser(client.post("/user/query") {
                    setBody("""{"uin":10001}""")
                }, status = 1, reason = "first")
            }
        }
    }

    @Test
    fun freshSchemaIsVersionedAndIdempotent() {
        h2DataSource().use { dataSource ->
            val db = initializeDatabase(dataSource)
            transaction(db) {
                assertEquals(1, SchemaVersions.selectAll().single()[SchemaVersions.version])
                assertFalse(SchemaVersions.selectAll().single()[SchemaVersions.legacyCleanupPending])
                assertEquals(tokenDigest("test_root"), Admins.selectAll().single()[Admins.tokenDigest])
                assertTrue(currentSchemaDifferences().isEmpty())
                Users.insert {
                    it[uin] = 42
                    it[status] = 7
                    it[reason] = "keep"
                    it[lastUpdate] = LEGACY_TIME
                }
            }

            initializeDatabase(dataSource)
            transaction(database(dataSource)) {
                assertEquals(1, SchemaVersions.selectAll().count())
                assertEquals(1, Admins.selectAll().count())
                assertEquals("keep", Users.selectAll().single()[Users.reason])
                assertTrue(currentSchemaDifferences().isEmpty())
            }
        }
    }

    @Test
    fun legacySchemaMigratesDataAndDropsLegacyTables() {
        h2DataSource().use { dataSource ->
            createLegacySchema(dataSource)
            val service = AuthService(initializeDatabase(dataSource))

            transaction(database(dataSource)) {
                val user = Users.selectAll().single()
                assertEquals(10001L, user[Users.uin])
                assertEquals(7, user[Users.status])
                assertEquals("legacy-user", user[Users.reason])

                val admin = Admins.selectAll().single()
                assertEquals(tokenDigest("test_root"), admin[Admins.tokenDigest])
                assertEquals("root", admin[Admins.nickname])
                assertEquals(1L, History.selectAll().single()[History.id])
                val card = Cards.selectAll().single()
                assertEquals("legacy-card", card[Cards.cardMsg])
                assertEquals("sendCard", card[Cards.type])
                assertEquals(1, SchemaVersions.selectAll().single()[SchemaVersions.version])
                assertFalse(SchemaVersions.selectAll().single()[SchemaVersions.legacyCleanupPending])
                assertTrue(currentSchemaDifferences().isEmpty())

                val tables = tableNames()
                assertFalse(setOf("user", "admin", "log", "card", "batchmsg").any(tables::contains))
            }

            testApplication {
                application { installAuthServer(service, Clock.System.now()) }
                assertUser(client.post("/user/query") {
                    setBody("""{"uin":10001}""")
                }, status = 7, reason = "legacy-user")
                assertCode(client.post("/user") {
                    setBody("""{"uin":10001,"status":8,"token":"test_root","reason":"migrated"}""")
                }, 200)
                assertCode(client.post("/admin") {
                    setBody("""{"desttoken":"child","nickname":"Child","token":"test_root"}""")
                }, 200)
                val history = client.get("/user/history") {
                    setBody("""{"uin":10001,"token":"test_root"}""")
                }.json()["history"]!!.jsonArray
                assertEquals(2, history.size)
                assertEquals("2", history.last().jsonObject["id"]!!.jsonPrimitive.content)
            }
        }
    }

    @Test
    fun previouslyRetainedBatchTableIsDropped() {
        h2DataSource().use { dataSource ->
            initializeDatabase(dataSource)
            transaction(database(dataSource)) {
                SchemaUtils.create(LegacyBatchMessages)
                LegacyBatchMessages.insert {
                    it[uin] = 10001
                    it[batchMsg] = "retained"
                    it[date] = LEGACY_TIME
                }
            }

            initializeDatabase(dataSource)

            transaction(database(dataSource)) {
                assertFalse("batchmsg" in tableNames())
                assertFalse(SchemaVersions.selectAll().single()[SchemaVersions.legacyCleanupPending])
            }
        }
    }

    @Test
    fun legacyStringUinsMigrateWithoutSchemaRewrite() {
        h2DataSource().use { dataSource ->
            transaction(database(dataSource)) {
                SchemaUtils.create(
                    StringLegacyUsers,
                    LegacyAdmins,
                    StringLegacyHistory,
                    StringLegacyCards,
                )
                StringLegacyUsers.batchInsert(1L..2_001L, shouldReturnGeneratedValues = false) { value ->
                    this[StringLegacyUsers.uin] = value.toString()
                    this[StringLegacyUsers.status] = 7
                    this[StringLegacyUsers.reason] = "legacy-$value"
                    this[StringLegacyUsers.lastUpdate] = LEGACY_TIME
                }
                LegacyAdmins.insert {
                    it[token] = "test_root"
                    it[nickname] = "root"
                    it[creator] = null
                    it[role] = 0
                    it[reason] = null
                    it[lastUpdate] = LEGACY_TIME
                }
                StringLegacyHistory.insert {
                    it[uin] = "10001"
                    it[operator] = "root"
                    it[operation] = "createUser"
                    it[changes] = "null -> 7"
                    it[reason] = "legacy-user"
                    it[date] = LEGACY_TIME
                }
                StringLegacyCards.insert {
                    it[uin] = "10001"
                    it[cardMsg] = "legacy-card"
                    it[type] = "copyCard"
                    it[date] = LEGACY_TIME
                }
            }

            initializeDatabase(dataSource)

            transaction(database(dataSource)) {
                assertEquals(2_001, Users.selectAll().count())
                listOf(2L, 10L, 1_899L, 2_001L).forEach { uin ->
                    assertEquals(
                        "legacy-$uin",
                        Users.selectAll().where { Users.uin eq uin }.single()[Users.reason],
                    )
                }
                assertEquals(10001L, History.selectAll().single()[History.uin])
                val card = Cards.selectAll().single()
                assertEquals(10001L, card[Cards.uin])
                assertEquals("copyCard", card[Cards.type])
                assertFalse(setOf("user", "admin", "log", "card").any(tableNames()::contains))
            }
        }
    }

    @Test
    fun concurrentUserCreationKeepsAuditHistoryConsistent() {
        h2DataSource().use { dataSource ->
            val service = AuthService(initializeDatabase(dataSource))
            testApplication {
                application { installAuthServer(service, Clock.System.now()) }
                val responses = coroutineScope {
                    listOf(2, 3).map { status ->
                        async {
                            client.post("/user") {
                                setBody(
                                    """{"uin":70001,"status":$status,"token":"test_root","reason":"s$status"}""",
                                )
                            }
                        }
                    }.awaitAll()
                }
                responses.forEach { assertCode(it, 200) }

                val history = client.get("/user/history") {
                    setBody("""{"uin":70001,"token":"test_root"}""")
                }.json()["history"]!!.jsonArray
                assertEquals(2, history.size)
                val changes = history.map { it.jsonObject["changes"]!!.jsonPrimitive.content }
                val firstStatus = changes.first().substringAfter("null -> ").toInt()
                assertTrue(firstStatus == 2 || firstStatus == 3)
                val finalStatus = changes.last().substringAfter(" -> ").toInt()
                assertEquals("$firstStatus -> $finalStatus", changes.last())
                assertTrue(finalStatus == 2 || finalStatus == 3)
                assertTrue(finalStatus != firstStatus)
                assertUser(client.post("/user/query") {
                    setBody("""{"uin":70001}""")
                }, status = finalStatus, reason = "s$finalStatus")
            }
        }
    }

    @Test
    fun unsafeSchemasAreRejectedWithoutSilentRepair() {
        h2DataSource().use { dataSource ->
            transaction(database(dataSource)) { SchemaUtils.create(BrokenLegacyUsers) }
            assertFailsWith<IllegalStateException> { initializeDatabase(dataSource) }
            transaction(database(dataSource)) {
                assertFalse("auth_schema_versions" in tableNames())
            }
        }

        h2DataSource().use { dataSource ->
            transaction(database(dataSource)) {
                SchemaUtils.create(Users)
                Users.insert {
                    it[uin] = 9
                    it[status] = 1
                    it[reason] = "unversioned"
                    it[lastUpdate] = LEGACY_TIME
                }
            }
            assertFailsWith<IllegalStateException> { initializeDatabase(dataSource) }
            transaction(database(dataSource)) {
                assertFalse("auth_schema_versions" in tableNames())
                assertEquals("unversioned", Users.selectAll().single()[Users.reason])
            }
        }

        h2DataSource().use { dataSource ->
            createLegacySchema(dataSource, duplicateAdmin = true)
            assertFailsWith<IllegalStateException> { initializeDatabase(dataSource) }
            transaction(database(dataSource)) {
                assertFalse("auth_schema_versions" in tableNames())
                assertEquals(2, LegacyAdmins.selectAll().count())
            }
        }

        h2DataSource().use { dataSource ->
            initializeDatabase(dataSource)
            transaction(database(dataSource)) {
                SchemaVersions.update({ SchemaVersions.version eq 1 }) {
                    it[version] = 2
                }
            }
            assertFailsWith<IllegalStateException> { initializeDatabase(dataSource) }
            transaction(database(dataSource)) {
                assertEquals(2, SchemaVersions.selectAll().single()[SchemaVersions.version])
                assertEquals(1, Admins.selectAll().count())
            }
        }

        h2DataSource().use { dataSource ->
            transaction(database(dataSource)) {
                SchemaUtils.create(Users, CorruptAdmins, History, Cards, SchemaVersions, SchemaLock)
                SchemaVersions.insert {
                    it[version] = 1
                    it[appliedAt] = LEGACY_TIME
                    it[legacyCleanupPending] = false
                }
            }
            assertFailsWith<IllegalStateException> { initializeDatabase(dataSource) }
            transaction(database(dataSource)) {
                assertEquals(0, CorruptAdmins.selectAll().count())
                assertEquals(1, SchemaVersions.selectAll().single()[SchemaVersions.version])
            }
        }

        h2DataSource().use { dataSource ->
            initializeDatabase(dataSource)
            transaction(database(dataSource)) {
                SchemaUtils.create(LegacyUsers)
                LegacyUsers.insert {
                    it[uin] = 99
                    it[status] = 1
                    it[reason] = "cleanup"
                    it[lastUpdate] = LEGACY_TIME
                }
            }
            assertFailsWith<IllegalStateException> { initializeDatabase(dataSource) }
            transaction(database(dataSource)) {
                assertTrue("user" in tableNames())
                assertEquals("cleanup", LegacyUsers.selectAll().single()[LegacyUsers.reason])
            }
        }

        h2DataSource().use { dataSource ->
            initializeDatabase(dataSource)
            transaction(database(dataSource)) {
                SchemaVersions.update({ SchemaVersions.version eq 1 }) {
                    it[legacyCleanupPending] = true
                }
                SchemaUtils.create(LegacyUsers)
                LegacyUsers.insert {
                    it[uin] = 99
                    it[status] = 1
                    it[reason] = "cleanup"
                    it[lastUpdate] = LEGACY_TIME
                }
            }
            initializeDatabase(dataSource)
            transaction(database(dataSource)) {
                assertFalse("user" in tableNames())
                assertFalse(SchemaVersions.selectAll().single()[SchemaVersions.legacyCleanupPending])
            }
        }
    }

    private fun createLegacySchema(dataSource: HikariDataSource, duplicateAdmin: Boolean = false) {
        transaction(database(dataSource)) {
            SchemaUtils.create(
                LegacyUsers,
                LegacyAdmins,
                LegacyHistory,
                LegacyCardsWithoutType,
                LegacyBatchMessages,
            )
            LegacyUsers.insert {
                it[uin] = 10001
                it[status] = 7
                it[reason] = "legacy-user"
                it[lastUpdate] = LEGACY_TIME
            }
            LegacyAdmins.insert {
                it[token] = "test_root"
                it[nickname] = "root"
                it[creator] = null
                it[role] = 0
                it[reason] = null
                it[lastUpdate] = LEGACY_TIME
            }
            if (duplicateAdmin) {
                LegacyAdmins.insert {
                    it[token] = "test_root"
                    it[nickname] = "duplicate"
                    it[creator] = null
                    it[role] = 1
                    it[reason] = null
                    it[lastUpdate] = LEGACY_TIME
                }
            }
            LegacyHistory.insert {
                it[uin] = 10001
                it[operator] = "root"
                it[operation] = "createUser"
                it[changes] = "null -> 7"
                it[reason] = "legacy-user"
                it[date] = LEGACY_TIME
            }
            LegacyCardsWithoutType.insert {
                it[uin] = 10001
                it[cardMsg] = "legacy-card"
                it[date] = LEGACY_TIME
            }
            LegacyBatchMessages.insert {
                it[uin] = 10001
                it[batchMsg] = "retained"
                it[date] = LEGACY_TIME
            }
        }
    }

    private fun database(dataSource: HikariDataSource): Database =
        Database.connect(datasource = dataSource)

    private fun currentSchemaDifferences(): List<String> =
        MigrationUtils.statementsRequiredForDatabaseMigration(
            Users,
            Admins,
            History,
            Cards,
            SchemaVersions,
            SchemaLock,
            withLogs = false,
        )

    private fun tableNames(): Set<String> = SchemaUtils.listTables()
        .mapTo(linkedSetOf()) { name ->
            name.substringAfterLast('.').trim('`', '"').lowercase()
        }

    private fun h2DataSource(): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:authserver_${UUID.randomUUID()};MODE=MariaDB;" +
                "DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 4
        },
    )

    private suspend fun assertCode(response: HttpResponse, expected: Int) {
        assertEquals(expected, response.status.value)
        assertEquals(expected, response.json()["code"]!!.jsonPrimitive.int)
    }

    private suspend fun assertUser(
        response: HttpResponse,
        status: Int,
        reason: String,
        lastUpdate: String? = null,
    ) {
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertEquals(200, body["code"]!!.jsonPrimitive.int)
        assertEquals(status, body["status"]!!.jsonPrimitive.int)
        assertEquals(reason, body["reason"]!!.jsonPrimitive.content)
        lastUpdate?.let { assertEquals(it, body["lastUpdate"]!!.jsonPrimitive.content) }
    }

    private suspend fun HttpResponse.json(): JsonObject =
        apiJson.parseToJsonElement(bodyAsText()).jsonObject

    private object BrokenLegacyUsers : Table("user") {
        val uin = long("uin")
        val status = integer("status")

        override val primaryKey = PrimaryKey(uin)
    }

    private object StringLegacyUsers : Table("user") {
        val uin = varchar("uin", 32)
        val status = integer("status")
        val reason = text("reason")
        val lastUpdate = datetime("lastupdate")

        override val primaryKey = PrimaryKey(uin)
    }

    private object StringLegacyHistory : Table("log") {
        val id = integer("id").autoIncrement()
        val uin = varchar("uin", 32)
        val operator = text("operator")
        val operation = text("operation")
        val changes = text("changes")
        val reason = text("reason")
        val date = datetime("date")

        override val primaryKey = PrimaryKey(id)
    }

    private object StringLegacyCards : Table("card") {
        val id = integer("id").autoIncrement()
        val uin = varchar("uin", 32)
        val cardMsg = largeText("cardmsg")
        val type = text("type")
        val date = datetime("date")

        override val primaryKey = PrimaryKey(id)
    }

    private object LegacyCardsWithoutType : Table("card") {
        val id = integer("id").autoIncrement()
        val uin = long("uin")
        val cardMsg = largeText("cardmsg")
        val date = datetime("date")

        override val primaryKey = PrimaryKey(id)
    }

    private object CorruptAdmins : Table("auth_admins") {
        val id = long("id").autoIncrement()
        val tokenDigest = char("token_digest", 64)
        val nickname = text("nickname")
        val creator = text("creator").nullable()
        val role = integer("role")
        val reason = text("reason").nullable()
        val lastUpdate = datetime("updated_at")

        override val primaryKey = PrimaryKey(id)
    }

    companion object {
        private val DATE_TIME = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")
        private val LEGACY_TIME = LocalDateTime(2020, 1, 2, 3, 4, 5)
    }
}
