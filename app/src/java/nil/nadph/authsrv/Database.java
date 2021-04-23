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
package nil.nadph.authsrv;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import kotlin.text.Regex;
import nil.nadph.authsrv.data.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

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

    public boolean validate(String token) {
        Regex regex = new Regex("^[0-9A-Za-z_]+$");
        if (regex.matches(token)) {
            try (PreparedStatement pstmt = db
                .prepareStatement("select * from admin where token = ?")) {
                pstmt.setString(1, token);
                ResultSet admin = pstmt.executeQuery();
                return admin.next();
            } catch (SQLException ex) {
                logger.error(ex);
                ex.printStackTrace();
                return false;
            }
        } else {
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
    public String updateUser(int uin, int status, @NotNull String token,
        String reason) {
        if (validate(token)) {
            try (
                PreparedStatement query = db.prepareCall("select * from user where uin = ?",
                    ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
                PreparedStatement insert = db.prepareStatement(
                    "insert into user (uin, status, reason, lastUpdate) values(?,?,?,now())");
                PreparedStatement log = db.prepareStatement(
                    "insert into log(uin, operator,operation, reason, date) values (?,(select nickname from admin where token = ?),'updateUser',?,now())")
            ) {
                query.setInt(1, uin);
                ResultSet rss = query.executeQuery();
                if (rss.next()) {
                    rss.updateInt("status", status);
                    rss.updateString("reason", reason);
                    rss.updateTimestamp("lastUpdate", new Timestamp(System.currentTimeMillis()));
                    rss.updateRow();
                } else {
                    insert.setInt(1, uin);
                    insert.setInt(2, status);
                    insert.setString(3, reason);
                    insert.executeUpdate();
                }
                log.setInt(1, uin);
                log.setString(2, token);
                log.setString(3, reason);
                log.executeUpdate();
                return resp.resp(200);
            } catch (SQLException ex) {
                logger.error(ex);
                ex.printStackTrace();
                return resp.resp(500);
            }
        } else {
            return resp.resp(401);
        }
    }

    /**
     * @param uin QQ号
     * @return -> README.md
     * @author gao_cai_sheng
     */
    public String queryUser(int uin) {
        try (PreparedStatement query = db.prepareStatement("select * from user where uin = ?")) {
            query.setInt(1, uin);
            ResultSet rs = query.executeQuery();
            if (rs.next()) {
                return resp.resp(200, rs.getInt("uin"), rs.getString("reason"),
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
    public String deleteUser(int uin, String token, String reason) {
        if (validate(token)) {
            try (PreparedStatement delete = db.prepareStatement("delete from user where uin = ?");
                PreparedStatement log = db.prepareStatement(
                    "insert into log(uin, operator,operation, reason, date) values (?,(select nickname from admin where token = ?),'deleteUser',?,now())")) {
                delete.setInt(1, uin);
                delete.executeUpdate();
                log.setInt(1, uin);
                log.setString(2, token);
                log.setString(3, reason);
                return resp.resp(200);
            } catch (SQLException throwable) {
                logger.error(throwable);
                throwable.printStackTrace();
                return resp.resp(500);
            }
        } else {
            return resp.resp(401);
        }
    }


    public String queryHistory(int uin, String token) {
        if (validate(token)) {
            try (PreparedStatement query = db.prepareStatement("select * from log where uin = ?")) {
                query.setInt(1, uin);
                ResultSet rs = query.executeQuery();
                return resp.resp(rs);

            } catch (SQLException throwable) {
                logger.error(throwable);
                throwable.printStackTrace();
                return resp.resp(500_1);
            }
        } else {
            return resp.resp(401);
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
    public String promoteAdmin(String destToken, String nickname, String reason, String token) {
        if (validate(token)) {
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
        } else {
            return resp.resp(401);
        }
    }

    /**
     * @param destToken 目标token
     * @param token     操作管理员token
     * @return 返回值
     * @author gao_cai_sheng
     */
    public String revokeAdmin(String destToken, String token) {
        if (validate(token)) {
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
        } else {
            return resp.resp(401);
        }
    }
}
