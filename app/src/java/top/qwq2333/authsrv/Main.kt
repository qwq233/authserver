/*
 * qwq233
 * Copyright (C) 2019-2021 qwq233@qwq2333.top
 * https://qwq2333.top
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by qwq233.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/qwq233/qwq233/blob/master/eula.md>.
 */
package top.qwq2333.authsrv

import com.alibaba.fastjson.JSONException
import com.alibaba.fastjson.JSONObject
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import top.qwq2333.authsrv.data.Response
import java.util.*

var errorCount: Long = 0
var requestCount: Long = 0


fun addRequestCount() {
    if (requestCount >= 1000000000L) {
        requestCount = 0
    }
    requestCount += 1L
}

fun addErrorCount() {
    if (errorCount >= 1000000000L) {
        errorCount = 0
    }
    errorCount += 1L
}

/**
 * @author gao_cai_sheng
 */
fun main() {
    val startTime = System.currentTimeMillis()
    val dbSource = Config("./config.json").dataSource
    Database.init(dbSource)
    val server = embeddedServer(Netty, 10810) {
        routing {
            get("/") {
                call.respondText(
                    "Server started at " + Date(startTime) + ", running for " +
                        (System.currentTimeMillis() - startTime) / 1000 + "s\nrequestCount = " + requestCount +
                        " \nError Count" + errorCount,
                    ContentType("application", "text"),
                )
            }

            // update user
            post("/user") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().updateUser(
                        req.getLong("uin"),
                        req.getIntValue("status"),
                        req.getString("token"),
                        req.getString("reason")
                    )
                    call.respondText(
                        response,
                        ContentType("application", "json"),
                        HttpStatusCode(
                            JSONObject.parseObject(response).getIntValue("code"),
                            JSONObject.parseObject(response).getString("reason")
                        )
                    )
                } catch (ex: NumberFormatException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (js: JSONException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, Response.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    addErrorCount()
                    call.application.log.error(ex)
                    call.respondText(
                        Response.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, Response.status(500_2))
                    )
                }
            }
            // query user
            post("/user/query") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().queryUser(req.getLong("uin"))
                    call.respondText(
                        response,
                        ContentType("application", "json"),
                        HttpStatusCode(
                            JSONObject.parseObject(response).getIntValue("code"),
                            JSONObject.parseObject(response).getString("reason")
                        )
                    )
                } catch (ex: NumberFormatException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (js: JSONException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, Response.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    addErrorCount()
                    call.application.log.error(ex)
                    call.respondText(
                        Response.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, Response.status(500_2))
                    )
                }
            }
            // delete user
            delete("/user") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().deleteUser(
                        req.getLong("uin"),
                        req.getString("token"),
                        req.getString("reason")
                    )
                    call.respondText(
                        response,
                        ContentType("application", "json"),
                        HttpStatusCode(
                            JSONObject.parseObject(response).getIntValue("code"),
                            JSONObject.parseObject(response).getString("reason")
                        )
                    )
                } catch (ex: NumberFormatException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (js: JSONException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, Response.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    addErrorCount()
                    call.application.log.error(ex)
                    call.respondText(
                        Response.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, Response.status(500_2))
                    )
                }
            }
            // promote admin
            post("/admin") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().promoteAdmin(
                        req.getString("desttoken"),
                        req.getString("nickname"),
                        req.getString("reason"),
                        req.getString("token")
                    )
                    call.respondText(
                        response,
                        ContentType("application", "json"),
                        HttpStatusCode(
                            JSONObject.parseObject(response).getIntValue("code"),
                            JSONObject.parseObject(response).getString("reason")
                        )
                    )
                } catch (js: JSONException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, Response.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: NumberFormatException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    addErrorCount()
                    call.application.log.error(ex)
                    call.respondText(
                        Response.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, Response.status(500_2))
                    )
                }

            }
            // revoke admin
            delete("/admin") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().revokeAdmin(
                        req.getString("desttoken"),
                        req.getString("token")
                    )
                    call.respondText(
                        response,
                        ContentType("application", "json"),
                        HttpStatusCode(
                            JSONObject.parseObject(response).getIntValue("code"),
                            JSONObject.parseObject(response).getString("reason")
                        )
                    )
                } catch (js: JSONException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, Response.status(400_2))
                    )
                } catch (ex: NumberFormatException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (npe: NullPointerException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    addErrorCount()
                    call.application.log.error(ex)
                    call.respondText(
                        Response.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, Response.status(500_2))
                    )
                }
            }
            get("/user/history") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().queryHistory(
                        req.getLong("uin"),
                        req.getString("token")
                    )
                    call.respondText(
                        response,
                        ContentType("application", "json"),
                        HttpStatusCode(
                            JSONObject.parseObject(response).getIntValue("code"),
                            JSONObject.parseObject(response).getString("reason")
                        )
                    )
                } catch (js: JSONException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_1),
                        ContentType("application", "json"),
                        HttpStatusCode(400, Response.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: NumberFormatException) {
                    addErrorCount()
                    call.respondText(
                        Response.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    addErrorCount()
                    ex.printStackTrace()
                    call.respondText(
                        Response.resp(500_1),
                        ContentType("application", "json"),
                        HttpStatusCode(500, Response.status(500_2))
                    )
                }
            }

        }
    }
    server.start(wait = true)

}
