/*
 * QNotified - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2021 dmca@ioctl.cc
 * https://github.com/ferredoxin/QNotified
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by ferredoxin.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/ferredoxin/QNotified/blob/master/LICENSE.md>.
 */
package nil.nadph.authsrv

import com.alibaba.fastjson.JSONObject
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import nil.nadph.authsrv.data.DatabaseConfig
import nil.nadph.authsrv.data.user.updateRequest


/**
 * @author gao_cai_sheng
 */
fun main() {
    val cf = Config()
    val js = JSONObject.parseObject(cf.readJsonFile("/home/qwq233/Project/nauth/config.json"))
    val getConfig = DatabaseConfig(
        js.getString("ip") + ":" + js.getString("port"),
        js.getString("username"),
        js.getString("password")
    )
    val config = HikariConfig()
    config.jdbcUrl =
        "jdbc:mysql://${getConfig.ip}/qn_auth?useUnicode=true&characterEncoding=utf8&useSSL=false"
    config.username = getConfig.username
    config.password = getConfig.password
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "300")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    val dbSource = HikariDataSource(config)
    val db = Database(dbSource.connection)
    val server = embeddedServer(Netty, 10810) {
        routing {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Plain)
            }
            post("/user/updateUser") {
                val readReq = JSONObject.parseObject(call.receiveText())
                val req = updateRequest(
                    readReq.getIntValue("uin"),
                    readReq.getIntValue("status"),
                    readReq.getString("token"),
                    readReq.getString("reason")
                )
                if (readReq != null) {
                    when (db.updateUser(req.uin, req.status, req.token, req.reason)) {
                        0 -> call.respondText(
                            "{\"code\": 200, \"reason\": \"\"}",
                            ContentType("application", "json")
                        )
                        1 -> call.respondText(
                            "{\"code\": 403,\"reason\": \"wrong token\"}",
                            ContentType("application", "json")
                        )
                        else -> call.respondText(
                            "{\"code\": 403,\"reason\": \"unknown error\"}\n",
                            ContentType("application", "json")
                        )
                    }
                } else {
                    call.respondText(
                        "{\"code\": 403,\"reason\": \"empty post message\"}\n",
                        ContentType("application", "json")
                    )
                }
            }
            post("/user/queryUser") {
                val readReq = JSONObject.parseObject(call.receiveText())
                if (readReq != null) {
                    val req = readReq.getIntValue("uin")
                    call.respondText(
                        db.queryUser(req),
                        ContentType("application", "json")
                    )
                }else {
                    call.respondText(
                        "{\"code\": 403,\"reason\": \"empty post message\"}\n",
                        ContentType("application", "json")
                    )
                }
            }
        }
    }
    server.start(wait = true)
}
