package cn.sh.nio;

/**
 * @author sh
 */
public class TimeServerApplication {

    public static void main(String[] args) {
        TimerServer timerServer = new TimerServer();
        new Thread(timerServer).start();
    }
}
