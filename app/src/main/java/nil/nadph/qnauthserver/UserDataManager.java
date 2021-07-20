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
    private final BusinessHandler businessHandler;
    private final File keyFile;
    private final File uinListFile;
    private final Logger logger;

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
        return 0;
    }

    public int getUserBlacklistFlags(long uin) {
        return 0b1000000000000000000000000000100;
    }

    public synchronized void setUserWhitelistFlags(long uin, int f) {
    }

    public synchronized void setUserBlacklistFlags(long uin, int f) {
    }

    public int checkAdminAccess(long token) {
        return 0;
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
