package nil.nadph.qnauthserver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

public class Logger {
    private final LinuxConsole console;

    public Logger(LinuxConsole c) {
        console = c;
    }

    public void logw(Throwable e) {
        logw(getStackTraceString(e));
    }

    public void loge(Throwable e) {
        loge(getStackTraceString(e));
    }

    public void loge(String msg) {
        Date d = new Date();
        console.printf("%02d-%02d %02d:%02d:%02d \033[31m[ERROR]\033[0m %s\n", d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), msg);
    }

    public void logw(String msg) {
        Date d = new Date();
        console.printf("%02d-%02d %02d:%02d:%02d \033[33m[WARN ]\033[0m %s\n", d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), msg);
    }

    public void logi(String msg) {
        Date d = new Date();
        console.printf("%02d-%02d %02d:%02d:%02d \033[34m[INFO ]\033[0m %s\n", d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), msg);
    }

    public void logv(String msg) {
        Date d = new Date();
        console.printf("%02d-%02d %02d:%02d:%02d \033[36m[VERBS]\033[0m %s\n", d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), msg);
    }

    public void logd(String msg) {
        Date d = new Date();
        console.printf("%02d-%02d %02d:%02d:%02d [DEBUG]\033[0m %s\n", d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds(), msg);
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
