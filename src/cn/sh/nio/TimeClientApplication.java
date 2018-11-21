package cn.sh.nio;

/**
 * @author sh
 */
public class TimeClientApplication {

    public static void main(String[] args) {
        TimeClient client = new TimeClient();
        new Thread(client).start();
    }
}
