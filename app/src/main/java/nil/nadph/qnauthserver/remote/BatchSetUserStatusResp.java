package nil.nadph.qnauthserver.remote;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import com.qq.taf.jce.JceStruct;
import nil.nadph.qnauthserver.JceId;

import java.io.IOException;

public class BatchSetUserStatusResp extends JceStruct {

    private static final long[] EMPTY_LONG_ARRAY = new long[0];
    private static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final String[] DUMMY_STRING_ARRAY = new String[]{""};
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    public static final int E_PERM = 1;
    public static final int E_INVALID_ARG = 2;

    @JceId(0)
    public int result;
    @JceId(1)
    public String msg = "";
    @JceId(2)
    public long[] vecUin = EMPTY_LONG_ARRAY;
    @JceId(3)
    public int[] currBlackFlags = EMPTY_INT_ARRAY;
    @JceId(4)
    public int[] currWhiteFlags = EMPTY_INT_ARRAY;
    @JceId(5)
    public int[] prevBlackFlags = EMPTY_INT_ARRAY;
    @JceId(6)
    public int[] prevWhiteFlags = EMPTY_INT_ARRAY;
    @JceId(7)
    public String[] comments = EMPTY_STRING_ARRAY;
    @JceId(8)
    public int count;

    @Override
    public void writeTo(JceOutputStream os) throws IOException {
        os.write(result, 0);
        os.write(msg, 1);
        os.write(vecUin, 2);
        os.write(currBlackFlags, 3);
        os.write(currWhiteFlags, 4);
        os.write(prevBlackFlags, 5);
        os.write(prevWhiteFlags, 6);
        os.write(comments, 7);
        os.write(count, 8);
    }

    @Override
    public void readFrom(JceInputStream is) throws IOException {
        result = is.read(0, 0, true);
        msg = is.read("", 1, true);
        vecUin = is.read(EMPTY_LONG_ARRAY, 2, true);
        currBlackFlags = is.read(EMPTY_INT_ARRAY, 3, true);
        currWhiteFlags = is.read(EMPTY_INT_ARRAY, 4, true);
        prevBlackFlags = is.read(EMPTY_INT_ARRAY, 5, true);
        prevWhiteFlags = is.read(EMPTY_INT_ARRAY, 6, true);
        comments = is.read(DUMMY_STRING_ARRAY, 7, true);
        count = is.read(0, 8, true);
    }
}
