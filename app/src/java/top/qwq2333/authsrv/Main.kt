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
                call.respondText("Hello, world!", ContentType.Text.Plain)
            }
            post("/user/updateUser") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().updateUser(
                        req.getIntValue("uin"),
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
                        HttpStatusCode(400, "Request Body should be a valid JSON object.")
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
                        HttpStatusCode(500, "Unknown error")
                    )
                }
            }
            post("/user/queryUser") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().queryUser(req.getIntValue("uin"))
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
                        HttpStatusCode(400, "Request Body should be a valid JSON object.")
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
                        HttpStatusCode(500, "Unknown error")
                    )
                }
            }
            post("/user/deleteUser") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().deleteUser(
                        req.getIntValue("uin"),
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
                        HttpStatusCode(400, "Request Body should be a valid JSON object.")
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
                        HttpStatusCode(500, "Unknown error")
                    )
                }
            }
            post("/admin/promoteAdmin") {
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
                        HttpStatusCode(400, "Request Body should be a valid JSON object.")
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
                        HttpStatusCode(500, "Unknown error")
                    )
                }

            }
            post("/admin/revokeAdmin") {
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
                        HttpStatusCode(400, "Request Body should be a valid JSON object.")
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
                        HttpStatusCode(500, "Unknown error")
                    )
                }
            }
            post("/user/queryHistory") {
                try {
                    val req = JSONObject.parseObject(call.receiveText())
                    val response = Database.getInstance().queryHistory(
                        req.getIntValue("uin"),
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
                        HttpStatusCode(400, "Request Body should be a valid JSON object.")
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
                        HttpStatusCode(500, "Unknown error")
                    )
                }
            }

        }
    }
    server.start(wait = true)
}
