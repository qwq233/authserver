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
package top.qwq2333.authsrv;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import kotlin.text.Regex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import top.qwq2333.authsrv.data.Response;

public class Database {

    public static final int MAX_SIZE = 20;
    private static final Logger logger = LogManager.getLogger(Database.class);
    private static final Database[] instance = new Database[MAX_SIZE];
    private static int pos = 0;
    private final Connection db;
    private final Response resp;

    private Database(Connection db) {
        this.db = db;
        this.resp = new Response();
    }

    public static void init(HikariDataSource db) {
        for (int i = 0; i < MAX_SIZE; i++) {
            try {
                instance[i] = new Database(db.getConnection());
            } catch (SQLException throwables) {
                throwables.printStackTrace();
                logger.error(throwables);
            }
        }
    }

    public static Database getInstance() {
        if (pos < MAX_SIZE) {
            pos++;
        } else {
            pos = 0;
        }
        return instance[pos];
    }

    /**
     * @param token 管理员token
     * @return token是否存在且合法
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean validate(String token) {
        Regex regex = new Regex("^[0-9A-Za-z_]+$");
        if (!regex.matches(token)) {
            return false;
        }
        try (PreparedStatement pstmt = db
            .prepareStatement("select * from admin where token = ?")) {
            pstmt.setString(1, token);
            ResultSet admin = pstmt.executeQuery();
            return admin.next();
        } catch (SQLException ex) {
            logger.error(ex);
            ex.printStackTrace();
            return false;
        } catch (NullPointerException npe) {
            return false;
        }
    }

    /**
     * @param token  管理员token
     * @param uin    QQ号
     * @param status 状态
     * @param reason 理由
     * @author gao_cai_sheng
     */
    public String updateUser(long uin, int status, @NotNull String token,
        String reason) {
        if (!validate(token)) {
            return resp.resp(401);
        }
        try (
            PreparedStatement query = db.prepareCall("select * from user where uin = ?",
                ResultSet.TYPE_SCROLL_SENSITIVE,
                ResultSet.CONCUR_UPDATABLE);
            PreparedStatement insert = db.prepareStatement(
                "insert into user (uin, status, reason, lastUpdate) values(?,?,?,now())");
            PreparedStatement log = db.prepareStatement(
                "insert into log(uin, operator, reason, date,changes,operation) "
                    + "values (?,(select nickname from admin where token = ?),"
                    + "?,now(),?,?)")
        ) {
            query.setLong(1, uin);
            ResultSet rss = query.executeQuery();
            if (rss.next()) {
                rss.updateInt("status", status);
                rss.updateString("reason", reason);
                rss.updateTimestamp("lastUpdate", new Timestamp(System.currentTimeMillis()));
                log.setString(4, rss.getInt("status") + " -> " + status);
                log.setString(5, "updateUser");
                rss.updateRow();
            } else {
                insert.setLong(1, uin);
                insert.setInt(2, status);
                insert.setString(3, reason);
                log.setString(5, "createUser");
                log.setString(4, "null -> " + status);
                insert.executeUpdate();
            }
            log.setLong(1, uin);
            log.setString(2, token);
            log.setString(3, reason);
            log.executeUpdate();
            return resp.resp(200);
        } catch (SQLException ex) {
            logger.error(ex);
            ex.printStackTrace();
            return resp.resp(500);
        }

    }

    /**
     * @param uin QQ号
     * @return -> README.md
     * @author gao_cai_sheng
     */
    public String queryUser(long uin) {
        try (PreparedStatement query = db.prepareStatement("select * from user where uin = ?")) {
            query.setLong(1, uin);
            ResultSet rs = query.executeQuery();
            if (rs.next()) {
                return resp.resp(200, rs.getInt("status"), rs.getString("reason"),
                    rs.getString("lastUpdate"));
            } else {
                return "{\"code\": 200,\"status\": 0,\"reason\": \"\",\"lastUpdate\": \"\"}\n";
            }
        } catch (SQLException ex) {
            logger.error(ex);
            ex.printStackTrace();
            return resp.resp(500);
        }
    }

    /**
     * @param uin    QQ号
     * @param token  管理员token
     * @param reason 理由
     * @return 返回值
     * @author gao_cai_sheng
     */
    public String deleteUser(long uin, @NotNull String token, String reason) {
        if (!validate(token)) {
            return resp.resp(401);
        }
        try (PreparedStatement delete = db.prepareStatement("delete from user where uin = ?");
            PreparedStatement log = db.prepareStatement(
                "insert into log(uin, operator,operation, reason, date,changes) values (?,(select nickname from admin where token = ?),'deleteUser',?,now(),?)")) {
            delete.setLong(1, uin);
            delete.executeUpdate();
            log.setLong(1, uin);
            log.setString(2, token);
            log.setString(3, reason);
            log.setString(4,
                JSON.parseObject(queryUser(uin)).getIntValue("status")
                    + " -> null");
            log.executeUpdate();
            return resp.resp(200);
        } catch (SQLException throwable) {
            logger.error(throwable);
            throwable.printStackTrace();
            return resp.resp(500);
        } catch (JSONException js) {
            return resp.resp(400_2);
        } catch (Exception ex) {
            logger.error(ex);
            ex.printStackTrace();
            return resp.resp(500_2);
        }
    }


    public String queryHistory(long uin, @NotNull String token) {
        if (!validate(token)) {
            return resp.resp(401);
        }
        try (PreparedStatement query = db.prepareStatement("select * from log where uin = ?")) {
            query.setLong(1, uin);
            ResultSet rs = query.executeQuery();
            return resp.resp(rs);

        } catch (SQLException throwable) {
            logger.error(throwable);
            throwable.printStackTrace();
            return resp.resp(500_1);
        }
    }


    /**
     * @param destToken 待添加token
     * @param token     管理员token
     * @param nickname  待添加管理员昵称
     * @param reason    理由
     * @return 返回值
     * @author gao_cai_sheng
     */
    public String promoteAdmin(@NotNull String destToken, String nickname, String reason,
        @NotNull String token) {
        if (!validate(token)) {
            return resp.resp(401);
        }
        try (PreparedStatement query = db
            .prepareStatement("select * from admin where token = ?");
            PreparedStatement promote = db.prepareStatement(
                "insert into admin(token, nickname, creator, role, reason, lastUpdate)values " +
                    "(?,?,(select * from ( (select nickname from admin where token = ?) ) as an),"
                    +
                    "( ( select * from (select role from admin where token= ?) as ar ) +1 ), ? ,now())")) {
            query.setString(1, destToken);
            ResultSet rs = query.executeQuery();
            if (rs.next()) {
                return "{\"code\": 403,\"reason\": \"dest admin token exist\"}";
            } else {
                promote.setString(1, destToken);
                promote.setString(2, nickname);
                promote.setString(3, token);
                promote.setString(4, token);
                promote.setString(5, reason);
                promote.executeUpdate();
                return resp.resp(200);
            }

        } catch (SQLException throwable) {
            logger.error(throwable);
            throwable.printStackTrace();
            return resp.resp(500);
        }
    }

    /**
     * @param destToken 目标token
     * @param token     操作管理员token
     * @return 返回值
     * @author gao_cai_sheng
     */
    public String revokeAdmin(String destToken, String token) {
        if (!validate(token)) {
            return resp.resp(401);
        }
        try (PreparedStatement query = db
            .prepareStatement("select role from admin where token = ?");
            PreparedStatement queryDest = db
                .prepareStatement("select role from admin where token = ?");
            PreparedStatement revoke = db
                .prepareStatement("delete from admin where token = ?")) {
            query.setString(1, token);
            ResultSet rs = query.executeQuery();
            rs.next();
            queryDest.setString(1, destToken);
            ResultSet destrs = queryDest.executeQuery();
            if (destrs.next()) {
                if (destrs.getInt("role") > rs.getInt("role")) {
                    revoke.setString(1, destToken);
                    revoke.executeUpdate();
                    return resp.resp(200);
                } else {
                    return resp.resp(403);
                }

            } else {
                return "{\"code\": 403,\"reason\": \"dest admin token is not exist\"}";
            }
        } catch (SQLException throwable) {
            logger.error(throwable);
            throwable.printStackTrace();
            return resp.resp(500);
        }
    }
}
