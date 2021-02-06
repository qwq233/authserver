package nil.nadph.qnauthserver;

import nil.nadph.qnauthserver.remote.*;

import java.io.IOException;
import java.util.Arrays;

public class BusinessHandler {
    private final UserDataManager userMgr;
    private final Logger logger;

    public BusinessHandler(AuthServer server) {
        logger = server.getLogger();
        userMgr = new UserDataManager(this);
    }

    public FromServiceMsg handleToServiceMsg(ToServiceMsg msg) {
        String name = msg.getServiceName();
        String cmd = msg.getServiceCmd();
        if ("".equals(name) || "".equals(cmd)) {
            return new FromServiceMsg(msg.getUniSeq(), 404, "invalid name");
        }
        try {
            if ("NAuth.QNotified".equals(name)) {
                switch (cmd) {
                    case "GetUserStatus": {
                        GetUserStatusReq req = new GetUserStatusReq(msg.getBody());
                        GetUserStatusResp resp = new GetUserStatusResp();
                        long uin = req.uin;
                        resp.uin = uin;
                        resp.blacklistFlags = userMgr.getUserBlacklistFlags(uin);
                        resp.whitelistFlags = userMgr.getUserWhitelistFlags(uin);
                        logger.logd("user " + uin + ": w:0x" + Integer.toHexString(resp.whitelistFlags) + ",b:0x" + Integer.toHexString(resp.blacklistFlags));
                        return new FromServiceMsg(msg.getUniSeq(), resp);
                    }
                    case "SetUserStatus": {
                        SetUserStatusReq req = new SetUserStatusReq(msg.getBody());
                        SetUserStatusResp resp = new SetUserStatusResp();
                        resp.uin = req.uin;
                        if (userMgr.checkAdminAccess(msg.getToken()) == 0) {
                            resp.result = SetUserStatusResp.E_PERM;
                        } else {
                            userMgr.setUserBlacklistFlags(req.uin, req.blacklistFlags);
                            userMgr.setUserWhitelistFlags(req.uin, req.whitelistFlags);
                            resp.result = 0;
                            logger.logi("op_token:" + msg.getToken() + ": set uin " + req.uin + " to w:0x" + Integer.toHexString(req.whitelistFlags) + ",b:0x" + Integer.toHexString(req.blacklistFlags));
                        }
                        return new FromServiceMsg(msg.getUniSeq(), resp);
                    }
                    case "AdminLogin": {
                        AdminLoginReq req = new AdminLoginReq(msg.getBody());
                        AdminLoginResp resp = new AdminLoginResp();
                        String key = req.opKey;
                        long t = userMgr.handleAdminLogin(key);
                        resp.token = t;
                        if (t == 0) {
                            resp.result = AdminLoginResp.E_INVALID_KEY;
                        } else {
                            resp.result = 0;
                            logger.logi("admin_login:" + t);
                        }
                        return new FromServiceMsg(msg.getUniSeq(), resp);
                    }
                    case "AdminCtl": {
                        AdminCtlReq req = new AdminCtlReq(msg.getBody());
                        AdminCtlResp resp = new AdminCtlResp();
                        int op = req.op;
                        int arg1 = req.arg1;
                        if (userMgr.checkAdminAccess(msg.getToken()) == 0) {
                            resp.result = SetUserStatusResp.E_PERM;
                        } else {
                            switch (op) {
                                case AdminCtlReq.OP_NOP:
                                    resp.result = 0;
                                    break;
                                case AdminCtlReq.OP_LOGOUT:
                                    userMgr.handleAdminLogout(msg.getToken());
                                    resp.result = 0;
                                    break;
                                default:
                                    resp.result = AdminCtlResp.E_INVALID_OP;
                                    break;
                            }
                            logger.logi("op_token:" + msg.getToken() + ": op=" + op + " arg1=" + arg1);
                        }
                        return new FromServiceMsg(msg.getUniSeq(), resp);
                    }
                    case "BatchSetUserStatus": {
                        BatchSetUserStatusReq req = Utf8JceUtils.decodeJceStruct(new BatchSetUserStatusReq(), msg.getBody());
                        BatchSetUserStatusResp resp = new BatchSetUserStatusResp();
                        if (userMgr.checkAdminAccess(msg.getToken()) == 0) {
                            resp.result = SetUserStatusResp.E_PERM;
                        } else {
                            userMgr.batchSetUserFlags(resp, req.count, req.vecUin, req.comment, req.blackSetFlags, req.blackClearFlags,
                                    req.whiteSetFlags, req.whiteClearFlags);
                            logger.logi(String.format("op_token:%d BatchSetUserStatus: %s,%x,%x,%x,%x,%s", msg.getToken(), Arrays.toString(req.vecUin),
                                    req.blackSetFlags, req.blackClearFlags, req.whiteSetFlags, req.whiteClearFlags, req.comment));
                        }
                        return new FromServiceMsg(msg.getUniSeq(), resp);
                    }
                    case "BatchQueryUserStatus": {
                        BatchQueryUserStatusReq req = Utf8JceUtils.decodeJceStruct(new BatchQueryUserStatusReq(), msg.getBody());
                        BatchQueryUserStatusResp resp = new BatchQueryUserStatusResp();
                        if (userMgr.checkAdminAccess(msg.getToken()) == 0) {
                            resp.result = SetUserStatusResp.E_PERM;
                        } else {
                            userMgr.batchQueryUserStatus(resp, req.count, req.vecUin);
                        }
                        return new FromServiceMsg(msg.getUniSeq(), resp);
                    }
                    default: {
                        return new FromServiceMsg(msg.getUniSeq(), 404, "command not found");
                    }
                }
            } else {
                return new FromServiceMsg(msg.getUniSeq(), 404, "service not found");
            }
        } catch (IOException e) {
            logger.logw(e);
            return new FromServiceMsg(msg.getUniSeq(), 500, "internal server error");
        }
    }

    public UserDataManager getUserManager() {
        return userMgr;
    }

    public Logger getLogger() {
        return logger;
    }
}
