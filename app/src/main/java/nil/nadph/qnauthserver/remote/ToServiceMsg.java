package nil.nadph.qnauthserver.remote;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import com.qq.taf.jce.JceStruct;

import java.io.IOException;
import java.util.Random;

public class ToServiceMsg extends JceStruct {
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final Random r = new Random();
    private int uniSeq;//0
    private String serviceName;//1
    private String serviceCmd;//2
    private long token;//3
    private byte[] body;//4

    public ToServiceMsg() {
        uniSeq = r.nextInt();
    }

    public ToServiceMsg(String name, String cmd, byte[] b, long t) {
        uniSeq = r.nextInt();
        serviceName = name;
        serviceCmd = cmd;
        body = b;
        token = t;
    }

    public byte[] getBody() {
        return body;
    }


    public int getUniSeq() {
        return uniSeq;
    }

    public String getServiceCmd() {
        return serviceCmd;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public void writeTo(JceOutputStream os) throws IOException {
        os.write(uniSeq, 0);
        os.write(serviceName, 1);
        os.write(serviceCmd, 2);
        os.write(token, 3);
        os.write(body, 4);
    }

    @Override
    public void readFrom(JceInputStream is) throws IOException {
        uniSeq = is.readInt(0, true);
        serviceName = is.readString(1, true);
        serviceCmd = is.readString(2, true);
        token = is.read(0L, 3, false);
        body = is.read(EMPTY_BYTE_ARRAY, 4, true);
    }

    public void ensureNonNull() {
        if (body == null) body = EMPTY_BYTE_ARRAY;
        if (serviceCmd == null) serviceCmd = "";
        if (serviceName == null) serviceName = "";
    }
}
