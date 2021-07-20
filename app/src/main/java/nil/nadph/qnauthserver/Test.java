package nil.nadph.qnauthserver;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Test {
    public static void main(String[] args) {
        String timeTag=new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss ").format(new Date());
        System.out.println(timeTag);

    }
}
