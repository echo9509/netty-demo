package cn.sh.sslsocket.server;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * @author sh
 */
public class SSLSocketServer {

    public static void main(String[] args) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException {
        //准备KeyStore相关信息
        String keyName = "SSL";
        String keyStoreName = "/Users/sh/workspace/netty-demo/src/cn/sh/sslsocket/seckey";
        char[] keyStorePwd = "123456".toCharArray();
        char[] keyPwd = "1234567890".toCharArray();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        //装载生成的seckey
        try(InputStream in = new FileInputStream(new File(keyStoreName))) {
            keyStore.load(in, keyStorePwd);
        }

        //初始化KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPwd);

        //初始化SSLContext
        SSLContext context = SSLContext.getInstance(keyName);
        context.init(kmf.getKeyManagers(), new TrustManager[]{getX509TrustManger()}, new SecureRandom());

        //监听和接受客户端连接
        SSLServerSocketFactory factory = context.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(10002);
        System.out.println("服务器端已启动!!!");
        //等待客户端连接
        Socket client = serverSocket.accept();
        System.out.println("客户端地址:" + client.getRemoteSocketAddress());
        //准备输出流，用于向客户端发送信息
        OutputStream output = client.getOutputStream();
        //获取输入流，用于读取客户端发送的信息
        InputStream in = client.getInputStream();
        byte[] buf = new byte[1024];
        int len;
        if ((len = in.read(buf)) != -1) {
            output.write(buf, 0, len);
        }
        //冲刷数据
        output.flush();
        //关闭输入输出流
        output.close();
        in.close();
        serverSocket.close();
    }


    public static X509TrustManager getX509TrustManger() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

}
