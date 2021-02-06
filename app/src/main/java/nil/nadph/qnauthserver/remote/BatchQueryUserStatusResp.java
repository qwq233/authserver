package nil.nadph.qnauthserver.remote;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import com.qq.taf.jce.JceStruct;
import nil.nadph.qnauthserver.JceId;

import java.io.IOException;

public class BatchQueryUserStatusResp extends JceStruct {

    private static final long[] EMPTY_LONG_ARRAY = new long[0];
    private static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final String[] DUMMY_STRING_ARRAY = new String[]{""};
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static final int E_PERM = 1;
    public static final int E_INVALID_ARG = 2;

    public static final byte STATUS_UNKNOWN = 0;
    public static final byte STATUS_ONLINE = 1;
    public static final byte STATUS_OFFLINE = 2;

    @JceId(0)
    public int result;
    @JceId(1)
    public String msg = "";
    @JceId(2)
    public long[] vecUin = EMPTY_LONG_ARRAY;
    @JceId(3)
    public int[] blackFlags = EMPTY_INT_ARRAY;
    @JceId(4)
    public int[] whiteFlags = EMPTY_INT_ARRAY;
    @JceId(5)
    public String[] comments = EMPTY_STRING_ARRAY;
    @JceId(6)
    public int count;
    @JceId(7)
    public byte[] onlineStatus = EMPTY_BYTE_ARRAY;

    @Override
    public void writeTo(JceOutputStream os) throws IOException {
        os.write(result, 0);
        os.write(msg, 1);
        os.write(vecUin, 2);
        os.write(blackFlags, 3);
        os.write(whiteFlags, 4);
        os.write(comments, 5);
        os.write(count, 6);
        os.write(onlineStatus, 7);
    }

    @Override
    public void readFrom(JceInputStream is) throws IOException {
        result = is.read(0, 0, true);
        msg = is.read("", 1, true);
        vecUin = is.read(EMPTY_LONG_ARRAY, 2, true);
        blackFlags = is.read(EMPTY_INT_ARRAY, 3, true);
        whiteFlags = is.read(EMPTY_INT_ARRAY, 4, true);
        comments = is.read(DUMMY_STRING_ARRAY, 5, true);
        count = is.read(0, 6, true);
        onlineStatus = is.read(EMPTY_BYTE_ARRAY, 7, true);
    }
}
