package cn.sh.sslsocket.client;

import cn.sh.sslsocket.server.SSLSocketServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * @author sh
 */
public class SSLSocketClient {

    public static void main(String[] args) throws NoSuchAlgorithmException, KeyManagementException, IOException {
        SSLContext context = SSLContext.getInstance("SSL");
        context.init(null, new TrustManager[]{SSLSocketServer.getX509TrustManger()}, new SecureRandom());
        SSLSocketFactory factory = context.getSocketFactory();

        SSLSocket sslSocket = (SSLSocket) factory.createSocket("localhost", 10002);
        OutputStream output = sslSocket.getOutputStream();
        InputStream input = sslSocket.getInputStream();
        output.write("I am SSLSocketClient".getBytes());
        output.flush();
        byte[] buf = new byte[1024];
        int len;
        StringBuilder builder = new StringBuilder();
        while ((len = input.read(buf)) != -1) {
            builder.append(new String(buf, 0, len));
        }
        output.close();
        System.out.println("client received:" + builder.toString());
    }
}
