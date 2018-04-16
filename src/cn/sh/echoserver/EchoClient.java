package cn.sh.echoserver;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @author sh
 */
public class EchoClient {

    private String host;

    private int port;

    public EchoClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void startClient() {
        EventLoopGroup group = new NioEventLoopGroup();
        //创建Bootstrap来引导启动客户端
        Bootstrap bootstrap = new Bootstrap();
        //设置EventLoopGroup到Bootstrap中，EventLoopGroup可以理解为是一个线程池，这个线程池用来处理连接、接受数据、发送数据
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                //设置连接的服务器地址
                .remoteAddress(host, port)
                //设置Handler，客户端成功连接服务器后就会被执行
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(new EchoClientHandler());
                    }
                });
        try {
            //调用Bootstrap.connect()连接服务器
            ChannelFuture future = bootstrap.connect().sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                System.out.println("释放资源");
                //关闭EventLoopGroup释放资源
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new EchoClient("127.0.0.1", 8080).startClient();
    }
}
