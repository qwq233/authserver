package nil.nadph.qnauthserver.remote;

import com.qq.taf.jce.JceInputStream;
import com.qq.taf.jce.JceOutputStream;
import com.qq.taf.jce.JceStruct;
import nil.nadph.qnauthserver.JceId;

import java.io.IOException;

public class BatchQueryUserStatusReq extends JceStruct {

    private static final long[] EMPTY_LONG_ARRAY = new long[0];

    @JceId(0)
    public long[] vecUin = EMPTY_LONG_ARRAY;
    @JceId(1)
    public int count;

    @Override
    public void writeTo(JceOutputStream os) throws IOException {
        os.write(vecUin, 0);
        os.write(count, 1);
    }

    @Override
    public void readFrom(JceInputStream is) throws IOException {
        vecUin = is.read(EMPTY_LONG_ARRAY, 0, true);
        count = is.read(0, 1, true);
    }
}
