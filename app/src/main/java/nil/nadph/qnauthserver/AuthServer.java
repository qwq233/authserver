package nil.nadph.qnauthserver;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import nil.nadph.qnauthserver.remote.FromServiceMsg;
import nil.nadph.qnauthserver.remote.ToServiceMsg;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthServer {
    protected final int port;
    public static final String KS_PASSWORD = "serverpass";
    protected ExecutorService tp = Executors.newCachedThreadPool();
    protected Thread consoleReaderThread = null;
    protected Thread serverMainThread;
    protected boolean isShuttingDown = false;
    protected LinuxConsole console;
    protected Logger logger;
    private long serverStartTime;
    volatile private int serverReqSeq;
    volatile private int serverReqErrCount;
    protected SSLServerSocket serverSocket;
    protected BusinessHandler businessHandler;

    public static final int CMD_HEARTBEAT = 0;
    public static final int CMD_KEEP_ALIVE = 1;

    public AuthServer(int p, LinuxConsole c, Logger l) throws IOException {
        port = p;
        console = c;
        logger = l;
        serverMainThread = Thread.currentThread();
        try {
            SSLContext ctx = SSLContext.getInstance("SSL");
            logger.logi("Server protocol: " + ctx.getProtocol());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream("cert/server.ks"), KS_PASSWORD.toCharArray());
            kmf.init(ks, KS_PASSWORD.toCharArray());
            ctx.init(kmf.getKeyManagers(), new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    logger.logi("checkClientTrusted: " + s);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    logger.logi("checkServerTrusted: " + s);
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    logger.logi("getAcceptedIssuers");
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            serverSocket = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(port);
            logger.logi("Server is listening at 0.0.0.0:" + port);
            serverStartTime = System.currentTimeMillis();
            tp.execute(new Runnable() {
                @Override
                public void run() {
                    String cmd = null;
                    Thread t = Thread.currentThread();
                    t.setName("Console Command Executor");
                    while (!isShuttingDown) {
                        try {
                            cmd = console.readLine();
                        } catch (IOError e) {
                            e.printStackTrace();
                        }
                        if (cmd == null || t.isInterrupted()) break;
                        if (cmd.length() != 0) {
                            AuthServer.this.dispatchCommandLine(cmd);
                        }
                    }
                }
            });
            businessHandler = new BusinessHandler(AuthServer.this);
            try {
                while (!isShuttingDown) {
                    Socket s = serverSocket.accept();
                    serverReqSeq++;
                    tp.execute(new ClientHandler(s));
                }
            } catch (SocketException e) {
                if (!isShuttingDown) {
                    logger.loge(e);
                }
            }
            if (!isShuttingDown) {
                logger.loge("Server loop exited unexpectedly!");
            }
            shutdown();
        } catch (GeneralSecurityException e) {
            logger.loge(e);
        }
    }

    public class ClientHandler implements Runnable {
        public final Socket socket;

        public ClientHandler(Socket s) {
            if (s == null) throw new NullPointerException();
            socket = s;
        }

        @Override
        public void run() {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
                socket.setSoTimeout(120_000);
                boolean longConn = false;
                do {
                    int type = readBe32(in);
                    if (type == 0) {
                        //ctl
                        int seq = readBe32(in);
                        int cmd = readBe32(in);
                        int arg = readBe32(in);
                        int val = -1;
                        switch (cmd) {
                            case CMD_HEARTBEAT: {
                                val = 0;
                                break;
                            }
                            case CMD_KEEP_ALIVE: {
                                longConn = arg != 0;
                                val = 0;
                                logger.logi(socket.getInetAddress() + " setKeepAlive: " + arg);
                                break;
                            }
                        }
                        writeBe32(out, 0);
                        writeBe32(out, seq);
                        writeBe32(out, val);
                        out.flush();
                    } else {
                        int size2 = readBe32(in);
                        if (type != size2) throw new IOException("size doesn't match " + type + "/" + size2);
                        if (type > 1024 * 1024) throw new IOException("recv size too big: " + type);
                        byte[] inbuf = new byte[type];
                        int done = 0;
                        int i;
                        while (done < type && (i = in.read(inbuf, done, type - done)) > 0) {
                            done += i;
                        }
                        if (done < type) throw new IOException("recv " + done + " less than expected " + type);
                        ToServiceMsg toServiceMsg = new ToServiceMsg();
                        JceInputStream jin = Utf8JceUtils.newInputStream(inbuf);
                        toServiceMsg.readFrom(jin);
                        toServiceMsg.ensureNonNull();
                        FromServiceMsg fromServiceMsg;
                        try {
                            fromServiceMsg = businessHandler.handleToServiceMsg(toServiceMsg);
                        } catch (Throwable e) {
                            fromServiceMsg = new FromServiceMsg(toServiceMsg.getUniSeq());
                            fromServiceMsg.setResultCode(500);
                            fromServiceMsg.setErrorMsg(e.toString());
                        }
                        JceOutputStream jceout = Utf8JceUtils.newOutputStream();
                        fromServiceMsg.writeTo(jceout);
                        byte[] buf = jceout.toByteArray();
                        writeBe32(out, buf.length);
                        writeBe32(out, buf.length);
                        out.write(buf);
                        out.flush();
                    }
                } while (longConn);
                in.close();
                out.close();
                socket.close();
            } catch (IOException e) {
                serverReqErrCount++;
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                logger.logw(socket.getRemoteSocketAddress() + ": " + e.toString());
            }
        }
    }

    public Logger getLogger() {
        return logger;
    }

    public void shutdown() {
        shutdown(false);
    }

    public void shutdown(boolean force) {
        if (isShuttingDown) return;
        try {
        } catch (Throwable e) {
            logger.loge(e);
            if (force) {
                logger.loge("Force shutting down... You will lose your unsaved data!");
            } else {
                logger.loge("Unable to shut down safely, use \"shutdown -f\" to force it(you will lose your unsaved data)");
                return;
            }
        }
        isShuttingDown = true;
        if (serverSocket != null) try {
            serverSocket.close();
        } catch (IOException e) {
            logger.logw("Error while shutting down server");
            logger.logw(e);
        }
        if (consoleReaderThread != null && consoleReaderThread.isAlive()) consoleReaderThread.interrupt();
        if (serverMainThread != null && serverMainThread.isAlive()) serverMainThread.interrupt();
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    public void dispatchCommand(String name, String[] argv) {
        switch (name) {
            case "exit":
            case "quit":
            case "q":
            case "stop":
            case "shutdown": {
                if (!isShuttingDown) {
                    logger.logi("Start server shutting down");
                    boolean force = false;
                    if (argv.length != 0 && "-f".equals(argv[0])) {
                        force = true;
                    }
                    shutdown(force);
                } else {
                    logger.logi("Already being shutting down!");
                }
                return;
            }
            case "status":
            case "s": {
                logger.logi("Server started at " + new Date(serverStartTime) + ", running for " +
                        (System.currentTimeMillis() - serverStartTime) / 1000 + "s, requestCount = " + serverReqSeq + "(-" + serverReqErrCount + " errors)");
                return;
            }
            case "h":
            case "help": {
                logger.logi("commands: stop status addadmin rmadmin");
                break;
            }
            case "addadmin": {
                if (argv.length == 0) {
                    logger.logi("usage: addadmin <admin key>");
                } else if (argv.length == 1) {
                    UserDataManager userMgr = businessHandler.getUserManager();
                    if (userMgr.addAdminKey(argv[0])) {
                        logger.logi("Operation success.");
                    } else {
                        logger.loge("Operation failure.");
                    }
                } else {
                    logger.loge("Too many arguments");
                }
                break;
            }
            case "rmadmin":
            case "deladmin": {
                if (argv.length == 0) {
                    logger.logi("usage: rmadmin <admin key>");
                } else if (argv.length == 1) {
                    UserDataManager userMgr = businessHandler.getUserManager();
                    if (userMgr.removeAdminKey(argv[0])) {
                        logger.logi("Revocation success.");
                    } else {
                        logger.loge("Operation failure.");
                    }
                } else {
                    logger.loge("Too many arguments");
                }
                break;
            }
            case "sync":
            case "save": {
                try {
                    businessHandler.getUserManager().requestSaveUinDat();
                } catch (Exception e) {
                    logger.loge(e);
                }
                break;
            }
            default:
                logger.logw("Unknown command " + name);
        }
    }

    public int getPort() {
        return port;
    }

    public void dispatchCommandLine(String cmdLine) {
        ArrayList<String> parsed = parseArguments(cmdLine);
        if (parsed.size() == 0) {
            return;
        }
        String cmd = parsed.remove(0).toLowerCase();
        String[] args = parsed.toArray(new String[0]);
        dispatchCommand(cmd, args);
    }

    /**
     * form Nukkit
     *
     * @param cmdLine
     * @return
     */
    private ArrayList<String> parseArguments(String cmdLine) {
        StringBuilder sb = new StringBuilder(cmdLine);
        ArrayList<String> args = new ArrayList<>();
        boolean notQuoted = true;
        int start = 0;
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\\') {
                sb.deleteCharAt(i);
                continue;
            }
            if (sb.charAt(i) == ' ' && notQuoted) {
                String arg = sb.substring(start, i);
                if (!arg.isEmpty()) {
                    args.add(arg);
                }
                start = i + 1;
            } else if (sb.charAt(i) == '"') {
                sb.deleteCharAt(i);
                --i;
                notQuoted = !notQuoted;
            }
        }
        String arg = sb.substring(start);
        if (!arg.isEmpty()) {
            args.add(arg);
        }
        return args;
    }

    public static void writeBe32(OutputStream out, int i) throws IOException {
        out.write((i >>> 24) & 0xFF);
        out.write((i >>> 16) & 0xFF);
        out.write((i >>> 8) & 0xFF);
        out.write(i & 0xFF);
    }

    public static int readBe32(InputStream in) throws IOException {
        int ret = 0;
        int i;
        if ((i = in.read()) < 0) throw new EOFException();
        ret |= (i & 0xFF) << 24;
        if ((i = in.read()) < 0) throw new EOFException();
        ret |= (i & 0xFF) << 16;
        if ((i = in.read()) < 0) throw new EOFException();
        ret |= (i & 0xFF) << 8;
        if ((i = in.read()) < 0) throw new EOFException();
        ret |= (i & 0xFF);
        return ret;
    }

}
