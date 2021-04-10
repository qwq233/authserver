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

import com.alibaba.fastjson.JSONObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class Database {

    private static final Logger logger = LogManager.getLogger(Database.class);
    private final Connection db;

    public Database(Connection db) {
        this.db = db;
    }

    public boolean validate(String token) {
        try (PreparedStatement pstmt = db.prepareStatement("select * from admin where token = ?")) {
            pstmt.setString(1, token);
            ResultSet admin = pstmt.executeQuery();
            return admin.next();
        } catch (SQLException ex) {
            logger.error(ex);
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * @param token  管理员token
     * @param uin    QQ号
     * @param status 状态
     * @param reason 理由
     * @return 0 success 1 token not-exist 2 unknown error
     * @author gao_cai_sheng
     */
    public int updateUser(int uin, int status, @NotNull String token,
        String reason) {
        try (
            PreparedStatement query = db.prepareCall("select * from user where uin = ?",
                ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            PreparedStatement insert = db.prepareStatement(
                "insert into user (uin, status, reason, lastUpdate) values(?,?,?,CURDATE())");
            PreparedStatement log = db.prepareStatement(
                "insert into log(uin, operator, reason, date) values (?,(select nickname from admin where token = ?),?,CURDATE())")
        ) {
            if (validate(token)) {
                query.setInt(1, uin);
                ResultSet rss = query.executeQuery();
                if (rss.next()) {
                    rss.first();
                    rss.updateInt(2, status);
                    rss.updateString(3, reason);
                    rss.updateRow();
                } else {
                    insert.setInt(1, uin);
                    insert.setInt(2, status);
                    insert.setString(3, reason);
                }
                log.setInt(1, uin);
                log.setString(2, token);
                log.setString(3, reason);
                log.executeUpdate();
                return 0;
            } else {
                return 1;
            }
        } catch (SQLException ex) {
            logger.error(ex);
            ex.printStackTrace();
            return 2;
        }
    }

    /**
     * @author gao_cai_sheng
     * @param uin QQ号
     * @return -> README.md
     */
    public String queryUser(int uin){
        try(PreparedStatement query = db.prepareStatement("select * from user where uin = ?")){
            query.setInt(1,uin);
            ResultSet rs = query.executeQuery();
            if(rs.next()){
                JSONObject resp = new JSONObject();
                resp.put("code",200);
                resp.put("status",rs.getInt(2));
                resp.put("reason",rs.getString(3));
                resp.put("lastUpdate",rs.getString(4));
                System.out.println(rs.getString(4));
                return resp.toJSONString();
            }else{
                return "{\"code\": 200,\"status\": 0,\"reason\": \"\",\"lastUpdate\": \"\"}\n";
            }
        }catch (SQLException ex) {
            logger.error(ex);
            ex.printStackTrace();
            return "{\"code\": 403,\"status\": 0,\"reason\": \"unknown error\",\"lastUpdate\": \"\"}";
        }
    }
}
