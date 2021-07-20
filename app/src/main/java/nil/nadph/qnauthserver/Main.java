package nil.nadph.qnauthserver;

public class Main {
    public static final int LISTEN_PORT = 8080;

    public static void main(String[] args) {
        final LinuxConsole console = new LinuxConsole();
        Logger logger = new Logger(console);
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            console.shutdown();
            throwable.printStackTrace();
            System.exit(2);
        });
        console.printf("Starting QNotified Auth Server at port %d...\n", LISTEN_PORT);
        try {
            new AuthServer(LISTEN_PORT, console, logger);
        } catch (Exception e) {
            logger.loge(e);
        }
        console.println("Server shutdown.");
        console.shutdown();
    }
}
