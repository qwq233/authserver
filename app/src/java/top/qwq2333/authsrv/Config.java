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

package top.qwq2333.authsrv;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Config {


    private static final Logger logger = LoggerFactory.getLogger(MainKt.class);
    private String jsonStr;

    /**
     * @param fileName 文件路径
     */
    public Config(String fileName) {
        File jsonFile = new File(fileName);
        try (Reader reader = new InputStreamReader(new FileInputStream(jsonFile),
            StandardCharsets.UTF_8)) {
            int ch;
            StringBuilder sb = new StringBuilder();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            jsonStr = sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            logger.error(String.valueOf(e));
        }
    }

    public HikariDataSource getDataSource() {
        new JSONObject();
        JSONObject jsonObject = JSON.parseObject(jsonStr);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(
            "jdbc:mysql://" + jsonObject.getString("ip") + ":" + jsonObject.getString("port")
                + "/qn_auth?useUnicode=true&characterEncoding=utf8&useSSL=false");
        config.setUsername(jsonObject.getString("username"));
        config.setPassword(jsonObject.getString("password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "300");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(Database.MAX_SIZE);
        return new HikariDataSource(config);
    }
}
