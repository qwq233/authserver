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
                if ("GetUserStatus".equals(cmd)) {
                    GetUserStatusReq req = new GetUserStatusReq(msg.getBody());
                    GetUserStatusResp resp = new GetUserStatusResp();
                    long uin = req.uin;
                    resp.uin = uin;
                    resp.blacklistFlags = userMgr.getUserBlacklistFlags(0);
                    resp.whitelistFlags = userMgr.getUserWhitelistFlags(0);
                    logger.logd("user " + uin + ": w:0x" + Integer.toHexString(resp.whitelistFlags) + ",b:0x" + Integer.toHexString(resp.blacklistFlags));
                    return new FromServiceMsg(msg.getUniSeq(), resp);
                }
                return new FromServiceMsg(msg.getUniSeq(), 404, "command not found");
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
