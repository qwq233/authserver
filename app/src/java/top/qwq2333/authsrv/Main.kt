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
import top.qwq2333.authsrv.data.Response


/**
 * @author gao_cai_sheng
 */
fun main() {
    val cf = Config("./config.json")
    val resp = Response()
    val dbSource = cf.dataSource
    Database.init(dbSource)
    val server = embeddedServer(Netty, 10810) {
        routing {
            get("/") {
                call.respondText(
                    "{\"code\":200,\"reason\":\"Everything is ok.\"}",
                    ContentType("application", "json"),
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
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (js: JSONException) {
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, resp.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    call.respondText(
                        resp.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respondText(
                        resp.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, resp.status(500_2))
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
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (js: JSONException) {
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, resp.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    call.respondText(
                        resp.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respondText(
                        resp.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, resp.status(500_2))
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
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (js: JSONException) {
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, resp.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    call.respondText(
                        resp.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respondText(
                        resp.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, resp.status(500_2))
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
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, resp.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    call.respondText(
                        resp.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: NumberFormatException) {
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respondText(
                        resp.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, resp.status(500_2))
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
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode(400, resp.status(400_2))
                    )
                } catch (ex: NumberFormatException) {
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (npe: NullPointerException) {
                    call.respondText(
                        resp.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respondText(
                        resp.resp(500_2),
                        ContentType("application", "json"),
                        HttpStatusCode(500, resp.status(500_2))
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
                    call.respondText(
                        resp.resp(400_1),
                        ContentType("application", "json"),
                        HttpStatusCode(400, resp.status(400_2))
                    )
                } catch (npe: NullPointerException) {
                    call.respondText(
                        resp.resp(400),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: NumberFormatException) {
                    call.respondText(
                        resp.resp(400_2),
                        ContentType("application", "json"),
                        HttpStatusCode.BadRequest
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    call.respondText(
                        resp.resp(500_1),
                        ContentType("application", "json"),
                        HttpStatusCode(500, resp.status(500_2))
                    )
                }
            }

        }
    }
    server.start(wait = true)
}
