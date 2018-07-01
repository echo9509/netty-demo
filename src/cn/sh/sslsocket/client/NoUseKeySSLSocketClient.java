package cn.sh.sslsocket.client;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author sh
 */
public class NoUseKeySSLSocketClient {

    public static void main(String[] args) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket sslSocket = (SSLSocket) factory.createSocket("localhost", 10002);
        OutputStream output = sslSocket.getOutputStream();
        InputStream input = sslSocket.getInputStream();
        output.write("I am NoUseKeySSLSocketClient".getBytes());
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
