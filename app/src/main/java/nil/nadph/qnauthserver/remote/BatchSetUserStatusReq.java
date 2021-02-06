package nil.nadph.qnauthserver.remote;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import com.qq.taf.jce.JceStruct;
import nil.nadph.qnauthserver.JceId;


import java.io.IOException;

public class BatchSetUserStatusReq extends JceStruct {

    private static final long[] EMPTY_LONG_ARRAY = new long[0];

    @JceId(0)
    public long[] vecUin = EMPTY_LONG_ARRAY;
    @JceId(1)
    public int blackSetFlags;
    @JceId(2)
    public int blackClearFlags;
    @JceId(3)
    public int whiteSetFlags;
    @JceId(4)
    public int whiteClearFlags;
    @JceId(5)
    public String comment = "";
    @JceId(6)
    public int count;

    @Override
    public void writeTo(JceOutputStream os) throws IOException {
        os.write(vecUin, 0);
        os.write(blackSetFlags, 1);
        os.write(blackClearFlags, 2);
        os.write(whiteSetFlags, 3);
        os.write(whiteClearFlags, 4);
        os.write(comment, 5);
        os.write(count, 6);
    }

    @Override
    public void readFrom(JceInputStream is) throws IOException {
        vecUin = is.read(EMPTY_LONG_ARRAY, 0, true);
        blackSetFlags = is.read(0, 1, true);
        blackClearFlags = is.read(0, 2, true);
        whiteSetFlags = is.read(0, 3, true);
        whiteClearFlags = is.read(0, 4, true);
        comment = is.read("", 5, true);
        count = is.read(0, 6, true);
    }
}
