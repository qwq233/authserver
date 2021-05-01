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

package top.qwq2333.authsrv.data;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class Response {

    private static final Logger logger = LogManager.getLogger(Response.class);
    /**
     * @param code 状态码 目前可用:200 400 401 403 500
     * @return 状态码对应的返回值
     */
    public String resp(int code) {
        return switch (code) {
            case 200 -> "{\"code\": 200, \"reason\": \"\"}";
            case 400 -> "{\"code\": 400,\"reason\": \"Request body is empty.\"}";
            case 400_1 -> "\"code\": 400,\"history\": \"\"}";
            case 400_2 -> "{\"code\": 400,\"reason\": \"Request Body should be a valid JSON object.\"}";
            case 401 -> "{\"code\": 401,\"reason\": \"Authentication failed\"}";
            case 403 -> "{\"code\": 403,\"reason\": \"You don't have permissions to do that\"}";
            case 500 -> "{\"code\": 500,\"reason\": \"Can't connect to the database."
                + "please try again later.\"}";
            case 500_1 -> "\"code\": 500,\"history\": \"\"}";
            case 500_2 -> "{\"code\": 500,\"reason\": \"Unknown error\"}";
            default -> "{\"code\": 500,\"reason\": \"Unknown error\"}";
        };
    }

    /**
     *
     * @param code 状态码
     * @param status 查询状态
     * @param reason 理由
     * @param lastUpdate 上次更新日期
     * @return 应返回值 json
     */
    public String resp(int code, int status, String reason, String lastUpdate) {
        JSONObject response = new JSONObject();
        response.put("code", code);
        response.put("status", status);
        response.put("reason", reason);
        response.put("lastUpdate", lastUpdate);
        return response.toJSONString();
    }

    public String resp(@NotNull ResultSet rs) {
        try {
            JSONArray array = new JSONArray();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                JSONObject jsonObj = new JSONObject();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    String value = rs.getString(columnName);
                    jsonObj.put(columnName, value);
                }
                array.add(jsonObj);
            }
            return "{\"code\":200,\"history\":" + array + "}";
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            logger.error(throwables);
            return this.resp(500_1);
        }
    }
}
