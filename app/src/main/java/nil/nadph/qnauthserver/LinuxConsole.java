package nil.nadph.qnauthserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Formatter;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class LinuxConsole {
    private final Object mOutputLock = new Object();
    protected boolean rawIoMode;
    private Thread consoleReaderThread;

    protected final PrintStream out;
    protected final InputStream in;
    protected final String tty;

    protected String prompt = "> ";
    protected boolean inputEof = false;
    protected int[] inputBuffer = new int[64];
    protected String inputBufferCachedStr = null;
    protected LinkedBlockingDeque<String> inputLines = new LinkedBlockingDeque<>();
    protected int inputIndex = 0;
    protected int inputLength = 0;
    protected boolean hasBrokenLine = false;
    protected final Set<Thread> blockingThreads = ConcurrentHashMap.newKeySet();

    int tmpBits = 0;
    int bytesLeft = 0;

    private boolean shutdown = false;

    protected boolean escStatus = false;
    protected StringBuilder afterStatus = new StringBuilder();

    public LinuxConsole() {
        this(System.in, System.out, new File("/dev/tty").exists() ? "/dev/tty" : null);
    }

    public LinuxConsole(InputStream _i, PrintStream _o, String _t) {
        in = _i;
        out = _o;
        tty = _t;
        if (tty != null) {
            File f = new File(tty);
            if (f.exists()) {
                try {
                    Process p = Runtime.getRuntime().exec("stty raw -echo -F " + tty);
                    rawIoMode = p.waitFor() == 0;
                } catch (IOException | InterruptedException e) {
                    rawIoMode = false;
                }
            }
        } else {
            rawIoMode = false;
        }
        if (rawIoMode) {
            out.print("\033[s");
            gotoInputBufferLocked();
            consoleReaderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    consoleReaderThread.setName("Console Reader");
                    try {
                        while (!shutdown && !consoleReaderThread.isInterrupted()) {
                            int i = in.read();
                            if (i < 0) {
                                inputEof = true;
                                for (Thread t : blockingThreads) {
                                    t.interrupt();
                                }
                                break;
                            } else {
                                i &= 0xFF;
                                if ((0x80 & i) == 0) {
                                    handleInputEvent(i);
                                } else {
                                    if ((0x40 & i) == 0) {
                                        //10xxxxxx
                                        tmpBits <<= 6;
                                        tmpBits |= i & 0b00111111;
                                        bytesLeft--;
                                        if (bytesLeft <= 0) {
                                            handleInputEvent(tmpBits);
                                            tmpBits = bytesLeft = 0;
                                        }
                                    } else {
                                        //1???0--
                                        int ii = i;
                                        int bts = 0;
                                        while ((0x80 & ii) != 0) {
                                            ii <<= 1;
                                            bts++;
                                        }
                                        tmpBits = (0xFF % (1 << (bts + 1))) & i;
                                        bytesLeft = bts - 1;
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            consoleReaderThread.start();
        }
    }

    protected void handleInputEvent(int i) {
        if (rawIoMode) {
            if (escStatus) {
                afterStatus.appendCodePoint(i);
                if (afterStatus.charAt(0) != '[') {
                    escStatus = false;
                    afterStatus = new StringBuilder();
                } else {
                    if (afterStatus.length() >= 2) {
                        switch (afterStatus.charAt(1)) {
                            case 'A':
                                onUpArrowKey();
                                break;
                            case 'B':
                                onDownArrowKey();
                                break;
                            case 'C':
                                onRightArrowKey();
                                break;
                            case 'D':
                                onLeftArrowKey();
                                break;
                        }
                        escStatus = false;
                        afterStatus = new StringBuilder();
                    }
                }
            } else {
                if (i == 27) {
                    escStatus = true;
                } else if (i == 127) {
                    removeCharBeforeCursor();
                } else {
                    insertCharAtCursor(i);
                }
            }
        } else {
            insertCharAtCursor(i);
        }
    }

    protected void onUpArrowKey() {

    }

    protected void onDownArrowKey() {

    }

    protected void onLeftArrowKey() {
        synchronized (mOutputLock) {
            if (inputIndex > 0) {
                inputIndex--;
                out.print("\033[1D");
            }
        }
    }

    protected void onRightArrowKey() {
        synchronized (mOutputLock) {
            if (inputIndex < inputLength) {
                inputIndex++;
                out.print("\033[1C");
            }
        }
    }

    protected void removeCharBeforeCursor() {
        synchronized (mOutputLock) {
            if (inputIndex <= 0) return;
            if (inputIndex == inputLength) {
                inputLength--;
                inputIndex--;
            } else if (inputIndex == 1) {
                System.arraycopy(inputBuffer, 1, inputBuffer, 0, inputLength - 1);
                inputIndex = 0;
                inputLength--;
            } else {
                System.arraycopy(inputBuffer, inputIndex, inputBuffer, inputIndex - 1, inputLength - inputIndex);
                inputIndex--;
                inputLength--;
            }
            inputBufferCachedStr = null;
            renderInputLineLocked();
        }
    }

    protected void insertCharAtCursor(int c) {
        if (c == 3) {
            shutdown();
            System.exit(255);
        }
        if (c != '\r' && c != '\n') {
            if (inputBuffer.length < inputLength + 2) {
                int[] buf2 = new int[(int) (inputBuffer.length * 1.5f + 2)];
                if (inputIndex == inputLength) {
                    System.arraycopy(inputBuffer, 0, buf2, 0, inputLength);
                    inputBuffer = buf2;
                    buf2[inputLength] = c;
                    inputLength++;
                    inputIndex++;
                } else {
                    if (inputIndex > 0) {
                        System.arraycopy(inputBuffer, 0, buf2, 0, inputIndex);
                    }
                    System.arraycopy(inputBuffer, inputIndex, buf2, inputIndex + 1, inputLength - inputIndex);
                    inputBuffer = buf2;
                    inputBuffer[inputIndex++] = c;
                    inputLength++;
                }
            } else {
                if (inputIndex == inputLength) {
                    inputBuffer[inputLength] = c;
                    inputLength++;
                    inputIndex++;
                } else {
                    System.arraycopy(inputBuffer, inputIndex, inputBuffer, inputIndex + 1, inputLength - inputIndex);
                    inputBuffer[inputIndex++] = c;
                    inputLength++;
                }
                inputBufferCachedStr = null;
                synchronized (mOutputLock) {
                    renderInputLineLocked();
                }
            }
        } else {
            String cmd;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < inputLength; i++) {
                sb.appendCodePoint(inputBuffer[i]);
            }
            cmd = sb.toString();
            synchronized (mOutputLock) {
                inputLength = 0;
                inputIndex = 0;
                inputBufferCachedStr = null;
                if (hasBrokenLine) {
                    out.print("\n\033[1G\033[K\033[s");
                    hasBrokenLine = false;
                } else {
                    out.print("\n\033[1G\033[K\033[s");
                }
                renderInputLineLocked();
            }
            inputLines.offer(cmd);
        }
    }

    private void gotoOutputBufferLocked() {
        if (hasBrokenLine) {
            out.print("\033[1G\033[1A\033[K");
        } else {
            out.print("\033[1G\033[K");
        }
    }

    private void gotoInputBufferLocked() {
        if (hasBrokenLine) {
            out.print("\033[s\n\033[1G");
        } else {
            out.print("\033[s\033[1G");
        }
        renderInputLineLocked();
    }

    private void renderInputLineLocked() {
        if (inputBufferCachedStr == null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < inputLength; i++) {
                sb.appendCodePoint(inputBuffer[i]);
            }
            inputBufferCachedStr = sb.toString();
        }
        out.print("\033[0m\033[1G\033[K" + prompt + inputBufferCachedStr);
        if (inputLength - inputIndex > 0) {
            out.print("\033[" + (inputLength - inputIndex) + "D");
        }
    }

    public boolean isRawIoMode() {
        return rawIoMode;
    }

    Formatter formatter = new Formatter();

    public LinuxConsole printf(String format, Object... args) {
        if (format == null) throw new NullPointerException("format == null");
        if (!shutdown) {
            print(String.format(format, args));
        } else {
            out.printf(format, args);
        }
        return this;
    }

    public void print(String str) {
        if (str == null || str.length() == 0) return;
        if (!shutdown) {
            synchronized (mOutputLock) {
                gotoOutputBufferLocked();
                if (str.contains("\r")) {
                    str = str.replace("\r\n", "\n").replace('\r', '\n');
                }
                hasBrokenLine = !str.endsWith("\n");
                str = str.replace("\n", "\n\033[1G\033[K");
                out.print(str);
                gotoInputBufferLocked();
            }
        } else {
            out.print(str);
        }
    }

    public void println(String str) {
        if (str == null) return;
        if (!shutdown) {
            synchronized (mOutputLock) {
                gotoOutputBufferLocked();
                hasBrokenLine = false;
                if (str.contains("\r")) {
                    str = str.replace("\r\n", "\n").replace('\r', '\n');
                }
                str = str.replace("\n", "\n\033[1G\033[K");
                out.print(str);
                out.print("\n\033[1G\033[K");
                gotoInputBufferLocked();
            }
        } else {
            out.println(str);
        }
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
        synchronized (mOutputLock) {
            renderInputLineLocked();
        }
    }

    public String getPrompt() {
        return prompt;
    }

    public String nextLine() {
        return readLine();
    }

    public String readLine() {
        if (inputEof) {
            try {
                return inputLines.removeFirst();
            } catch (NoSuchElementException e) {
                return null;
            }
        } else {
            Thread t = Thread.currentThread();
            boolean i = Thread.interrupted();
            try {
                blockingThreads.add(t);
                return inputLines.takeFirst();
            } catch (InterruptedException e) {
                //noinspection ResultOfMethodCallIgnored
                Thread.interrupted();
                return null;
            } finally {
                blockingThreads.remove(t);
                if (i) t.interrupt();
            }
        }
    }

    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        if (consoleReaderThread != null) {
            consoleReaderThread.interrupt();
        }
        if (rawIoMode) {
            try {
                Process p = Runtime.getRuntime().exec("stty -raw echo -F " + tty);
                rawIoMode = p.waitFor() != 0;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException ignored) {
            }
        }
        out.flush();
        System.exit(0);
    }

    private static void dbg() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
