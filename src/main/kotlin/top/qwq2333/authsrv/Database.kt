package top.qwq2333.authsrv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.SQLException
import java.util.HexFormat
import javax.sql.DataSource

internal object Users : Table("auth_users") {
    val uin = long("uin")
    val status = integer("status")
    val reason = text("reason")
    val lastUpdate = datetime("updated_at")

    override val primaryKey = PrimaryKey(uin)
}

internal object Admins : Table("auth_admins") {
    val id = long("id").autoIncrement()
    val tokenDigest = char("token_digest", TOKEN_DIGEST_LENGTH)
        .uniqueIndex("uq_auth_admins_token_digest")
    val nickname = text("nickname")
    val creator = text("creator").nullable()
    val role = integer("role")
    val reason = text("reason").nullable()
    val lastUpdate = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)
}

internal object History : Table("user_history") {
    val id = long("id").autoIncrement()
    val uin = long("uin")
    val operator = text("operator")
    val operation = text("operation")
    val changes = text("changes")
    val reason = text("reason")
    val date = datetime("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_user_history_uin_id", false, uin, id)
    }
}

internal object Cards : Table("card_events") {
    val id = long("id").autoIncrement()
    val uin = long("uin")
    val cardMsg = largeText("card_message")
    val type = text("type")
    val date = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

internal object SchemaVersions : Table("auth_schema_versions") {
    val version = integer("version")
    val appliedAt = datetime("applied_at")
    val legacyCleanupPending = bool("legacy_cleanup_pending")

    override val primaryKey = PrimaryKey(version)
}

internal object SchemaLock : Table("auth_schema_lock") {
    val id = integer("id")

    override val primaryKey = PrimaryKey(id)
}

internal object LegacyUsers : Table("user") {
    val uin = long("uin")
    val status = integer("status")
    val reason = text("reason")
    val lastUpdate = datetime("lastupdate")

    override val primaryKey = PrimaryKey(uin)
}

internal object LegacyAdmins : Table("admin") {
    val id = integer("id").autoIncrement()
    val token = text("token")
    val nickname = text("nickname")
    val creator = text("creator").nullable()
    val role = integer("role")
    val reason = text("reason").nullable()
    val lastUpdate = datetime("lastupdate")

    override val primaryKey = PrimaryKey(id)
}

internal object LegacyHistory : Table("log") {
    val id = integer("id").autoIncrement()
    val uin = long("uin")
    val operator = text("operator")
    val operation = text("operation")
    val changes = text("changes")
    val reason = text("reason")
    val date = datetime("date")

    override val primaryKey = PrimaryKey(id)
}

internal object LegacyCards : Table("card") {
    val id = integer("id").autoIncrement()
    val uin = long("uin")
    val cardMsg = largeText("cardmsg")
    val type = text("type")
    val date = datetime("date")

    override val primaryKey = PrimaryKey(id)
}

internal object LegacyBatchMessages : Table("batchmsg") {
    val id = integer("id").autoIncrement()
    val uin = long("uin")
    val batchMsg = largeText("batchmsg")
    val date = datetime("date")

    override val primaryKey = PrimaryKey(id)
}

internal fun initializeDatabase(dataSource: DataSource): Database =
    Database.connect(datasource = dataSource).also(DatabaseSchema::migrate)

private object DatabaseSchema {
    private val logger = LoggerFactory.getLogger(DatabaseSchema::class.java)
    private val currentTables = listOf(Users, Admins, History, Cards, SchemaVersions, SchemaLock)
    private val currentDataTables = listOf(Users, Admins, History, Cards)
    private val currentTableNames = currentTables.mapTo(linkedSetOf()) { it.normalizedName }
    private val legacyTables = listOf(LegacyUsers, LegacyAdmins, LegacyHistory, LegacyCards)
    private val legacyTableNames = legacyTables.mapTo(linkedSetOf()) { it.normalizedName }

    fun migrate(database: Database) {
        val createCurrentSchema = transaction(database) {
            requireSupportedDialect()
            val tableNames = tableNames()
            when (readVersion(tableNames)) {
                null -> {
                    validateUnversionedState(tableNames)
                    true
                }
                CURRENT_SCHEMA_VERSION -> {
                    validateCurrentSchema()
                    validateLegacyCleanupState(tableNames)
                    false
                }
                else -> error("Unsupported database schema version")
            }
        }

        if (createCurrentSchema) {
            transaction(database) {
                SchemaUtils.create(*currentTables.toTypedArray())
            }
        }

        val generatedAdminToken = transaction(database) {
            requireSupportedDialect()
            if (SchemaLock.selectAll().where { SchemaLock.id eq SCHEMA_LOCK_ID }.singleOrNull() == null) {
                SchemaLock.insert { it[id] = SCHEMA_LOCK_ID }
            }
            commit()
            SchemaLock.selectAll()
                .where { SchemaLock.id eq SCHEMA_LOCK_ID }
                .forUpdate()
                .single()

            val tableNames = tableNames()
            when (readVersion(tableNames)) {
                null -> migrateUnversioned(tableNames)
                CURRENT_SCHEMA_VERSION -> {
                    validateCurrentSchema()
                    validateLegacyCleanupState(tableNames)
                }
                else -> error("Unsupported database schema version")
            }
            bootstrapInitialAdmin()
        }
        generatedAdminToken?.let { token -> logger.warn("Initial administrator token: {}", token) }

        cleanupLegacyTables(database)
    }

    private fun migrateUnversioned(tableNames: Set<String>) {
        validateLegacyPresence(tableNames)
        validateCurrentSchema()
        check(currentDataTables.none { it.selectAll().limit(1).any() }) {
            "Refusing to overwrite unversioned data in the new schema"
        }
        val presentLegacyTables = tableNames.intersect(legacyTableNames)
        val migratedLegacy = presentLegacyTables == legacyTableNames
        if (migratedLegacy) {
            validateLegacySchema()
            migrateLegacyData()
        }

        validateCurrentSchema()
        SchemaVersions.insert {
            it[version] = CURRENT_SCHEMA_VERSION
            it[appliedAt] = CurrentDateTime
            it[SchemaVersions.legacyCleanupPending] = migratedLegacy
        }
        logger.info("Database schema migrated to version {}", CURRENT_SCHEMA_VERSION)
    }

    private fun validateLegacyPresence(tableNames: Set<String>) {
        val present = tableNames.intersect(legacyTableNames)
        check(present.isEmpty() || present == legacyTableNames) {
            "Refusing partial legacy schema: ${present.sorted()}"
        }
    }

    private fun validateUnversionedState(tableNames: Set<String>) {
        validateLegacyPresence(tableNames)
        if (tableNames.intersect(legacyTableNames) == legacyTableNames) {
            validateLegacySchema()
        }
        currentTables
            .filter { it.normalizedName in tableNames.intersect(currentTableNames) }
            .forEach { table ->
                check(schemaDifferences(listOf(table)).isEmpty()) {
                    "Unversioned table ${table.tableName} does not match the new schema"
                }
            }
        check(
            currentDataTables
                .filter { it.normalizedName in tableNames }
                .none { it.selectAll().limit(1).any() },
        ) {
            "Refusing unversioned data in the new schema"
        }
    }

    private fun validateLegacySchema() {
        // MariaDB legacy aliases and DATETIME precision vary by server version. A typed
        // Exposed projection validates the readable migration contract without rewriting it.
        legacyTables.forEach { table -> table.selectAll().limit(1).toList() }
        legacyAdminRows()
    }

    private fun validateCurrentSchema() {
        check(schemaDifferences(currentTables).isEmpty()) {
            "Database schema does not match version $CURRENT_SCHEMA_VERSION"
        }
    }

    private fun validateLegacyCleanupState(tableNames: Set<String>) {
        check(isLegacyCleanupPending() || tableNames.intersect(legacyTableNames).isEmpty()) {
            "Refusing unexpected legacy tables after completed migration"
        }
    }

    private fun schemaDifferences(tables: List<Table>): List<String> =
        MigrationUtils.statementsRequiredForDatabaseMigration(
            *tables.toTypedArray(),
            withLogs = false,
        )

    private fun migrateLegacyData() {
        val admins = legacyAdminRows()
        copyUsers()
        Admins.batchInsert(admins, shouldReturnGeneratedValues = false) { admin ->
            this[Admins.id] = admin.id.toLong()
            this[Admins.tokenDigest] = tokenDigest(admin.token)
            this[Admins.nickname] = admin.nickname
            this[Admins.creator] = admin.creator
            this[Admins.role] = admin.role
            this[Admins.reason] = admin.reason
            this[Admins.lastUpdate] = admin.lastUpdate
        }
        val migratedAdmins = Admins.selectAll().orderBy(Admins.id to SortOrder.ASC).toList()
        check(migratedAdmins.size == admins.size)
        admins.zip(migratedAdmins).forEach { (legacy, migrated) ->
            check(migrated[Admins.id] == legacy.id.toLong())
            check(migrated[Admins.tokenDigest] == tokenDigest(legacy.token))
            check(migrated[Admins.nickname] == legacy.nickname)
            check(migrated[Admins.creator] == legacy.creator)
            check(migrated[Admins.role] == legacy.role)
            check(migrated[Admins.reason] == legacy.reason)
            check(migrated[Admins.lastUpdate] == legacy.lastUpdate)
        }
        copyHistory()
        copyCards()

        check(Users.selectAll().count() == LegacyUsers.selectAll().count())
        check(Admins.selectAll().count() == LegacyAdmins.selectAll().count())
        check(History.selectAll().count() == LegacyHistory.selectAll().count())
        check(Cards.selectAll().count() == LegacyCards.selectAll().count())
    }

    private fun legacyAdminRows(): List<LegacyAdmin> {
        val rows = LegacyAdmins.selectAll()
            .orderBy(LegacyAdmins.id to SortOrder.ASC)
            .map { row ->
                LegacyAdmin(
                    id = row[LegacyAdmins.id],
                    token = row[LegacyAdmins.token],
                    nickname = row[LegacyAdmins.nickname],
                    creator = row[LegacyAdmins.creator],
                    role = row[LegacyAdmins.role],
                    reason = row[LegacyAdmins.reason],
                    lastUpdate = row[LegacyAdmins.lastUpdate],
                )
            }
        val digests = hashSetOf<String>()
        rows.forEach { admin ->
            check(tokenPattern.matches(admin.token)) {
                "Invalid legacy administrator token at id=${admin.id}"
            }
            check(digests.add(tokenDigest(admin.token))) {
                "Duplicate legacy administrator token at id=${admin.id}"
            }
        }
        return rows
    }

    private fun copyUsers() {
        var offset = 0L
        do {
            val rows = LegacyUsers.selectAll()
                .orderBy(LegacyUsers.uin to SortOrder.ASC)
                .limit(MIGRATION_BATCH_SIZE)
                .offset(offset)
                .toList()
            Users.batchInsert(rows, shouldReturnGeneratedValues = false) { row ->
                this[Users.uin] = row[LegacyUsers.uin]
                this[Users.status] = row[LegacyUsers.status]
                this[Users.reason] = row[LegacyUsers.reason]
                this[Users.lastUpdate] = row[LegacyUsers.lastUpdate]
            }
            val migrated = Users.selectAll()
                .where { Users.uin inList rows.map { it[LegacyUsers.uin] } }
                .associateBy { it[Users.uin] }
            check(migrated.size == rows.size)
            rows.forEach { legacy ->
                val current = checkNotNull(migrated[legacy[LegacyUsers.uin]])
                check(current[Users.uin] == legacy[LegacyUsers.uin])
                check(current[Users.status] == legacy[LegacyUsers.status])
                check(current[Users.reason] == legacy[LegacyUsers.reason])
                check(current[Users.lastUpdate] == legacy[LegacyUsers.lastUpdate])
            }
            offset += rows.size.toLong()
        } while (rows.size == MIGRATION_BATCH_SIZE)
    }

    private fun copyHistory() {
        var cursor: Int? = null
        do {
            val query = cursor?.let { value ->
                LegacyHistory.selectAll().where { LegacyHistory.id greater value }
            } ?: LegacyHistory.selectAll()
            val rows = query.orderBy(LegacyHistory.id to SortOrder.ASC)
                .limit(MIGRATION_BATCH_SIZE)
                .toList()
            History.batchInsert(rows, shouldReturnGeneratedValues = false) { row ->
                this[History.id] = row[LegacyHistory.id].toLong()
                this[History.uin] = row[LegacyHistory.uin]
                this[History.operator] = row[LegacyHistory.operator]
                this[History.operation] = row[LegacyHistory.operation]
                this[History.changes] = row[LegacyHistory.changes]
                this[History.reason] = row[LegacyHistory.reason]
                this[History.date] = row[LegacyHistory.date]
            }
            val migrated = History.selectAll()
                .where { History.id inList rows.map { it[LegacyHistory.id].toLong() } }
                .orderBy(History.id to SortOrder.ASC)
                .toList()
            check(migrated.size == rows.size)
            rows.zip(migrated).forEach { (legacy, current) ->
                check(current[History.id] == legacy[LegacyHistory.id].toLong())
                check(current[History.uin] == legacy[LegacyHistory.uin])
                check(current[History.operator] == legacy[LegacyHistory.operator])
                check(current[History.operation] == legacy[LegacyHistory.operation])
                check(current[History.changes] == legacy[LegacyHistory.changes])
                check(current[History.reason] == legacy[LegacyHistory.reason])
                check(current[History.date] == legacy[LegacyHistory.date])
            }
            cursor = rows.lastOrNull()?.get(LegacyHistory.id)
        } while (rows.size == MIGRATION_BATCH_SIZE)
    }

    private fun copyCards() {
        var cursor: Int? = null
        do {
            val query = cursor?.let { value ->
                LegacyCards.selectAll().where { LegacyCards.id greater value }
            } ?: LegacyCards.selectAll()
            val rows = query.orderBy(LegacyCards.id to SortOrder.ASC)
                .limit(MIGRATION_BATCH_SIZE)
                .toList()
            Cards.batchInsert(rows, shouldReturnGeneratedValues = false) { row ->
                this[Cards.id] = row[LegacyCards.id].toLong()
                this[Cards.uin] = row[LegacyCards.uin]
                this[Cards.cardMsg] = row[LegacyCards.cardMsg]
                this[Cards.type] = row[LegacyCards.type]
                this[Cards.date] = row[LegacyCards.date]
            }
            val migrated = Cards.selectAll()
                .where { Cards.id inList rows.map { it[LegacyCards.id].toLong() } }
                .orderBy(Cards.id to SortOrder.ASC)
                .toList()
            check(migrated.size == rows.size)
            rows.zip(migrated).forEach { (legacy, current) ->
                check(current[Cards.id] == legacy[LegacyCards.id].toLong())
                check(current[Cards.uin] == legacy[LegacyCards.uin])
                check(current[Cards.cardMsg] == legacy[LegacyCards.cardMsg])
                check(current[Cards.type] == legacy[LegacyCards.type])
                check(current[Cards.date] == legacy[LegacyCards.date])
            }
            cursor = rows.lastOrNull()?.get(LegacyCards.id)
        } while (rows.size == MIGRATION_BATCH_SIZE)
    }

    private fun bootstrapInitialAdmin(): String? {
        if (Admins.selectAll().limit(1).any()) return null

        val configured = System.getenv(INITIAL_ADMIN_ENV)?.takeIf(String::isNotBlank)
        val token = configured ?: HexFormat.of().formatHex(
            ByteArray(INITIAL_ADMIN_BYTES).also(SecureRandom()::nextBytes),
        )
        require(tokenPattern.matches(token)) {
            "$INITIAL_ADMIN_ENV must match ${tokenPattern.pattern}"
        }
        Admins.insert {
            it[Admins.tokenDigest] = tokenDigest(token)
            it[Admins.nickname] = "initial-admin"
            it[Admins.creator] = null
            it[Admins.role] = 0
            it[Admins.reason] = null
            it[Admins.lastUpdate] = CurrentDateTime
        }
        return token.takeIf { configured == null }
    }

    private fun readVersion(tableNames: Set<String>): Int? {
        if (SchemaVersions.normalizedName !in tableNames) return null
        return SchemaVersions.selectAll()
            .orderBy(SchemaVersions.version to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.get(SchemaVersions.version)
    }

    private fun tableNames(): Set<String> = SchemaUtils.listTables()
        .mapTo(linkedSetOf()) { name ->
            name.substringAfterLast('.')
                .trim('`', '"')
                .lowercase()
        }

    private fun requireSupportedDialect() {
        check(currentDialect is MariaDBDialect || currentDialect is H2Dialect) {
            "Unsupported database dialect: ${currentDialect.name}"
        }
    }

    private fun cleanupLegacyTables(database: Database) {
        transaction(database) {
            if (readVersion(tableNames()) != CURRENT_SCHEMA_VERSION) return@transaction
            if (!isLegacyCleanupPending()) return@transaction
            val existing = tableNames()
            val obsolete = legacyTables.filter { it.normalizedName in existing }
            if (obsolete.isNotEmpty()) SchemaUtils.drop(*obsolete.toTypedArray())
            check(tableNames().intersect(legacyTableNames).isEmpty()) {
                "Legacy table cleanup did not complete"
            }
            SchemaVersions.update({ SchemaVersions.version eq CURRENT_SCHEMA_VERSION }) {
                it[SchemaVersions.legacyCleanupPending] = false
            }
        }
    }

    private fun isLegacyCleanupPending(): Boolean = SchemaVersions.selectAll()
        .orderBy(SchemaVersions.version to SortOrder.DESC)
        .limit(1)
        .single()[SchemaVersions.legacyCleanupPending]

    private val Table.normalizedName: String
        get() = tableName.substringAfterLast('.').trim('`', '"').lowercase()

    private data class LegacyAdmin(
        val id: Int,
        val token: String,
        val nickname: String,
        val creator: String?,
        val role: Int,
        val reason: String?,
        val lastUpdate: LocalDateTime,
    )
}

internal class AuthService(private val database: Database) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    suspend fun updateUser(uin: Long, status: Int, token: String, reason: String): ApiResponse =
        databaseCall {
            val actor = authenticate(token)
            if (actor == null) {
                authenticationFailed()
            } else {
                val existing = Users.selectAll()
                    .where { Users.uin eq uin }
                    .forUpdate()
                    .singleOrNull()
                if (existing == null) {
                    Users.insert {
                        it[Users.uin] = uin
                        it[Users.status] = status
                        it[Users.reason] = reason
                        it[Users.lastUpdate] = CurrentDateTime
                    }
                    insertHistory(uin, actor.nickname, "createUser", "null -> $status", reason)
                } else {
                    val oldStatus = existing[Users.status]
                    Users.update({ Users.uin eq uin }) {
                        it[Users.status] = status
                        it[Users.reason] = reason
                        it[Users.lastUpdate] = CurrentDateTime
                    }
                    insertHistory(uin, actor.nickname, "updateUser", "$oldStatus -> $status", reason)
                }
                successResponse()
            }
        }

    suspend fun queryUser(uin: Long): ApiResponse = databaseCall {
        val row = Users.selectAll().where { Users.uin eq uin }.singleOrNull()
        if (row == null) {
            UserResponse(200, 0, "", "")
        } else {
            UserResponse(
                code = 200,
                status = row[Users.status],
                reason = row[Users.reason],
                lastUpdate = row[Users.lastUpdate].apiString(),
            )
        }
    }

    suspend fun deleteUser(uin: Long, token: String, reason: String): ApiResponse = databaseCall {
        val actor = authenticate(token)
        if (actor == null) {
            authenticationFailed()
        } else {
            val existing = Users.selectAll()
                .where { Users.uin eq uin }
                .forUpdate()
                .singleOrNull()
            if (existing != null) {
                Users.deleteWhere { Users.uin eq uin }
                insertHistory(
                    uin,
                    actor.nickname,
                    "deleteUser",
                    "${existing[Users.status]} -> null",
                    reason,
                )
            }
            successResponse()
        }
    }

    suspend fun queryHistory(uin: Long, token: String): ApiResponse = databaseCall {
        if (authenticate(token) == null) {
            authenticationFailed()
        } else {
            HistoryResponse(
                code = 200,
                history = History.selectAll()
                    .where { History.uin eq uin }
                    .orderBy(History.id to SortOrder.ASC)
                    .map(::historyEntry),
            )
        }
    }

    suspend fun promoteAdmin(
        destinationToken: String,
        nickname: String,
        reason: String?,
        token: String,
    ): ApiResponse {
        if (!tokenPattern.matches(destinationToken)) return BasicResponse(400, INVALID_REQUEST_REASON)
        return databaseCall(uniqueViolation = BasicResponse(403, ADMIN_EXISTS_REASON)) {
            val actor = authenticate(token)
            if (actor == null) {
                authenticationFailed()
            } else if (findAdmin(destinationToken) != null) {
                BasicResponse(403, ADMIN_EXISTS_REASON)
            } else {
                Admins.insert {
                    it[Admins.tokenDigest] = tokenDigest(destinationToken)
                    it[Admins.nickname] = nickname
                    it[Admins.creator] = actor.nickname
                    it[Admins.role] = actor.role + 1
                    it[Admins.reason] = reason
                    it[Admins.lastUpdate] = CurrentDateTime
                }
                successResponse()
            }
        }
    }

    suspend fun revokeAdmin(destinationToken: String, token: String): ApiResponse = databaseCall {
        val actor = authenticate(token)
        if (actor == null) {
            authenticationFailed()
        } else {
            val destination = findAdmin(destinationToken)
            when {
                destination == null -> BasicResponse(403, ADMIN_MISSING_REASON)
                destination.role <= actor.role -> forbidden()
                else -> {
                    Admins.deleteWhere { Admins.tokenDigest eq tokenDigest(destinationToken) }
                    successResponse()
                }
            }
        }
    }

    suspend fun sendCard(uin: Long, message: String): ApiResponse = databaseCall {
        Cards.insert {
            it[Cards.uin] = uin
            it[Cards.cardMsg] = message
            it[Cards.type] = "sendCard"
            it[Cards.date] = CurrentDateTime
        }
        successResponse()
    }

    private suspend fun databaseCall(
        uniqueViolation: ApiResponse? = null,
        block: () -> ApiResponse,
    ): ApiResponse = try {
        withContext(Dispatchers.IO) {
            suspendTransaction(db = database) { block() }
        }
    } catch (error: SQLException) {
        if (uniqueViolation != null && error.isUniqueViolation()) {
            uniqueViolation
        } else {
            logger.error("Database operation failed", error)
            databaseError()
        }
    }

    private fun authenticate(token: String): AdminIdentity? {
        if (!tokenPattern.matches(token)) return null
        return findAdmin(token)
    }

    private fun findAdmin(token: String): AdminIdentity? = Admins.selectAll()
        .where { Admins.tokenDigest eq tokenDigest(token) }
        .singleOrNull()
        ?.let { AdminIdentity(it[Admins.nickname], it[Admins.role]) }

    private fun insertHistory(
        uin: Long,
        operator: String,
        operation: String,
        changes: String,
        reason: String,
    ) {
        History.insert {
            it[History.uin] = uin
            it[History.operator] = operator
            it[History.operation] = operation
            it[History.changes] = changes
            it[History.reason] = reason
            it[History.date] = CurrentDateTime
        }
    }

    private fun historyEntry(row: ResultRow) = HistoryEntry(
        id = row[History.id].toString(),
        uin = row[History.uin].toString(),
        operator = row[History.operator],
        operation = row[History.operation],
        changes = row[History.changes],
        reason = row[History.reason],
        date = row[History.date].apiString(),
    )

    private data class AdminIdentity(val nickname: String, val role: Int)
}

internal fun tokenDigest(token: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

private fun LocalDateTime.apiString(): String = buildString(19) {
    append(year.toString().padStart(4, '0'))
    append('-')
    append((month.ordinal + 1).toString().padStart(2, '0'))
    append('-')
    append(day.toString().padStart(2, '0'))
    append(' ')
    append(hour.toString().padStart(2, '0'))
    append(':')
    append(minute.toString().padStart(2, '0'))
    append(':')
    append(second.toString().padStart(2, '0'))
}

private fun SQLException.isUniqueViolation(): Boolean =
    sqlState == "23505" || sqlState?.startsWith("23") == true && errorCode == 1062

private const val CURRENT_SCHEMA_VERSION = 1
private const val SCHEMA_LOCK_ID = 1
private const val MIGRATION_BATCH_SIZE = 1_000
private const val TOKEN_DIGEST_LENGTH = 64
private const val INITIAL_ADMIN_BYTES = 32
private const val INITIAL_ADMIN_ENV = "AUTH_INITIAL_ADMIN_TOKEN"
