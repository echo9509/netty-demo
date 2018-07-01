package cn.sh.sslsocket.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author sh
 */
public class SocketClient {

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 10002);
        OutputStream output = socket.getOutputStream();
        InputStream input = socket.getInputStream();
        output.write("I am SocketClient".getBytes());
        output.flush();
        byte[] buf = new byte[1024];
        int len;
        StringBuilder builder = new StringBuilder();
        while ((len = input.read(buf)) != -1) {
            builder.append(new String(buf, 0, len));
        }
        System.out.println("client received:" + builder.toString());
    }
}
