package nil.nadph.qnauthserver;

import nil.nadph.qnauthserver.remote.BatchQueryUserStatusResp;
import nil.nadph.qnauthserver.remote.BatchSetUserStatusResp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class UserDataManager {
    private final ConcurrentHashMap<Long, UserInfo> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AdminInfo> onlineOp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> opKeys = new ConcurrentHashMap<>();
    private final Object rwUinLock = new Object();
    private BusinessHandler businessHandler;
    private File keyFile;
    private File uinListFile;
    private Logger logger;

    public UserDataManager(BusinessHandler h) {
        businessHandler = h;
        logger = h.getLogger();
        keyFile = new File("data/adminKeys.txt").getAbsoluteFile();
        uinListFile = new File("data/uinList.dat").getAbsoluteFile();
        if (!keyFile.exists()) throw new IllegalStateException("admin key file " + keyFile.getPath() + " not exists");
        if (!uinListFile.exists())
            throw new IllegalStateException("uin data file " + uinListFile.getPath() + " not exists");
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(keyFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(fin));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.replace(" ", "").replace("\n", "").replace("\t", "").replace("\r", "");
                if (line.length() > 0) {
                    opKeys.put(line, (long) 0);
                    logger.logd("Read admin key: " + line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (fin != null) {
                try {
                    fin.close();
                } catch (IOException ignored) {
                }
            }
        }
        doReadUinFromDat();
    }

    public void requestSaveUinDat() {
        doSaveUinToDat();
    }

    private void doReadUinFromDat() {
        int sSize = -1;
        synchronized (rwUinLock) {
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(uinListFile);
                if (uinListFile.length() == 0) {
                    logger.logw("uinListFile length is 0, not to load");
                    return;
                }
                DataInputStream din = new DataInputStream(fin);
                int count = din.readInt();
                long uin;
                int w, b;
                int rsv;
                String comments;
                UserInfo info;
                for (int i = 0; i < count; i++) {
                    uin = din.readLong();
                    b = din.readInt();
                    w = din.readInt();
                    rsv = din.readInt();
                    if (rsv != 0) {
                        byte[] buf = new byte[rsv];
                        din.readFully(buf);
                        comments = new String(buf, StandardCharsets.UTF_8);
                    } else {
                        comments = "";
                    }
                    info = new UserInfo();
                    info.uin = uin;
                    info.whitelistFlags = w;
                    info.blacklistFlags = b;
                    info.comment = comments;
                    users.put(uin, info);
                }
                if (count != din.readInt()) {
                    throw new IOException("corrupted data, expected " + count);
                }
                din.close();
                sSize = count;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (fin != null) {
                    try {
                        fin.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        logger.logi("Load user data end, entry count = " + sSize);
    }

    private void doSaveUinToDat() {
        int sSize = -1;
        synchronized (rwUinLock) {
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(uinListFile);
                DataOutputStream dout = new DataOutputStream(fout);
                HashMap<Long, UserInfo> copy = new HashMap<>(users);
                int count = copy.size();
                long uin;
                int w, b;
                String comments;
                UserInfo info;
                dout.writeInt(count);
                for (Map.Entry<Long, UserInfo> ent : copy.entrySet()) {
                    uin = ent.getKey();
                    info = ent.getValue();
                    b = info.blacklistFlags;
                    w = info.whitelistFlags;
                    comments = info.comment;
                    dout.writeLong(uin);
                    dout.writeInt(b);
                    dout.writeInt(w);
                    if (comments != null && comments.length() > 0) {
                        byte[] bs = comments.getBytes(StandardCharsets.UTF_8);
                        dout.writeInt(bs.length);
                        dout.write(bs);
                    } else {
                        dout.writeInt(0);
                    }
                }
                dout.writeInt(count);
                dout.flush();
                dout.close();
                sSize = count;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (fout != null) {
                    try {
                        fout.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        logger.logi("User data saved, entry count = " + sSize);
    }

    public boolean addAdminKey(String k) {
        if (k == null) {
            return false;
        }
        k = k.replace(" ", "").replace("\n", "").replace("\t", "").replace("\r", "");
        if (k.length() == 0) {
            return false;
        }
        opKeys.put(k, 0L);
        saveAdminKeysToFile();
        return true;
    }

    public boolean removeAdminKey(String k) {
        if (k == null || k.length() == 0) return false;
        Long tokenObj = opKeys.remove(k);
        if (tokenObj == null) return false;
        onlineOp.remove(tokenObj);
        saveAdminKeysToFile();
        return true;
    }

    private void saveAdminKeysToFile() {
        try {
            FileOutputStream fout = new FileOutputStream(keyFile);
            for (Map.Entry<String, Long> ent : opKeys.entrySet()) {
                String k = ent.getKey();
                fout.write(k.getBytes(StandardCharsets.UTF_8));
                fout.write('\n');
            }
            fout.flush();
            fout.close();
        } catch (IOException e) {
            businessHandler.getLogger().loge(e);
        }
    }

    public int getUserWhitelistFlags(long uin) {
        UserInfo info = users.get(uin);
        if (info == null) return 0;
        return info.whitelistFlags;
    }

    public int getUserBlacklistFlags(long uin) {
        UserInfo info = users.get(uin);
        if (info == null) return 0;
        return info.blacklistFlags;
    }

    public synchronized void setUserWhitelistFlags(long uin, int f) {
        UserInfo info = users.get(uin);
        if (info != null) {
            info.whitelistFlags = f;
        } else {
            info = new UserInfo();
            users.put(uin, info);
            info.uin = uin;
            info.whitelistFlags = f;
            info.blacklistFlags = 0;
        }
    }

    public synchronized void setUserBlacklistFlags(long uin, int f) {
        UserInfo info = users.get(uin);
        if (info != null) {
            info.blacklistFlags = f;
        } else {
            info = new UserInfo();
            users.put(uin, info);
            info.uin = uin;
            info.whitelistFlags = 0;
            info.blacklistFlags = f;
        }
    }

    public synchronized void batchSetUserFlags(BatchSetUserStatusResp resp, int count, long[] vecUin, String comment, int bs, int bc, int ws, int wc) {
        if (count != vecUin.length) {
            resp.result = BatchQueryUserStatusResp.E_INVALID_ARG;
            resp.msg = "array length mismatch";
            return;
        }
        String timeTag = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss ").format(new Date());
        resp.vecUin = vecUin;
        resp.prevBlackFlags = new int[count];
        resp.prevWhiteFlags = new int[count];
        resp.currBlackFlags = new int[count];
        resp.currWhiteFlags = new int[count];
        resp.comments = new String[count];
        resp.count = count;
        for (int i = 0; i < count; i++) {
            long uin = vecUin[i];
            UserInfo info = users.get(uin);
            if (info == null) {
                info = new UserInfo();
                users.put(uin, info);
                info.uin = uin;
                info.blacklistFlags = bs;
                info.whitelistFlags = ws;
                info.comment = timeTag + comment + "\n";
                resp.currBlackFlags[i] = bs;
                resp.currWhiteFlags[i] = ws;
                resp.comments[i] = info.comment;
            } else {
                resp.prevBlackFlags[i] = info.blacklistFlags;
                resp.prevWhiteFlags[i] = info.whitelistFlags;
                info.blacklistFlags |= bs;
                info.whitelistFlags |= ws;
                info.blacklistFlags &= ~bc;
                info.whitelistFlags &= ~wc;
                info.comment += timeTag + comment + "\n";
                resp.currBlackFlags[i] = info.blacklistFlags;
                resp.currWhiteFlags[i] = info.whitelistFlags;
                resp.comments[i] = info.comment;
            }
        }
    }

    public void batchQueryUserStatus(BatchQueryUserStatusResp resp, int count, long[] vecUin) {
        if (count != vecUin.length) {
            resp.result = BatchQueryUserStatusResp.E_INVALID_ARG;
            resp.msg = "array length mismatch";
            return;
        }
        resp.vecUin = vecUin;
        resp.blackFlags = new int[count];
        resp.whiteFlags = new int[count];
        resp.onlineStatus = new byte[count];
        resp.comments = new String[count];
        resp.count = count;
        for (int i = 0; i < count; i++) {
            UserInfo info = users.get(vecUin[i]);
            if (info == null) {
                resp.comments[i] = "";
            } else {
                resp.blackFlags[i] = info.blacklistFlags;
                resp.whiteFlags[i] = info.whitelistFlags;
                resp.comments[i] = info.comment;
            }
        }
    }

    public int checkAdminAccess(long token) {
        AdminInfo ai = onlineOp.get(token);
        if (ai == null) return 0;
        long t = System.currentTimeMillis();
        if (ai.expire < t) {
            onlineOp.remove(token);
            return 0;
        }
        return 1;
    }

    public long handleAdminLogin(String key) {
        Long lastObj = opKeys.get(key);
        if (lastObj == null) return 0;
        long last = lastObj;
        if (last != 0) {
            onlineOp.remove(last);
        }
        AdminInfo ai = new AdminInfo();
        ai.key = key;
        long t = System.currentTimeMillis();
        ai.loginTime = t;
        ai.expire = t + 1000 * 3600;
        long token = new Random().nextLong();
        ai.token = token;
        opKeys.put(key, token);
        onlineOp.put(token, ai);
        return token;
    }

    public void handleAdminLogout(long token) {
        onlineOp.remove(token);
    }


    public static class UserInfo {
        public long uin;//0
        public int blacklistFlags;//1
        public int whitelistFlags;//2
        public String comment = "";//3
    }

    public static class AdminInfo {
        public long token;
        public String key;
        public long loginTime;
        public long expire;
    }
}
