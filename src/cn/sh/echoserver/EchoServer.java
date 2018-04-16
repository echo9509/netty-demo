package cn.sh.echoserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author sh
 */
public class EchoServer {

    private int port;

    public EchoServer(int port) {
        this.port = port;
    }

    public void startServer() {
        //指定使用NioEventLoopGroup来接受和处理新连接，接受和写数据等等
        EventLoopGroup group = new NioEventLoopGroup();
        //创建ServerBootstrap实例来引导绑定和启动服务器
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(group)
                //指定通道类型为NioServerSocketChannel
                .channel(NioServerSocketChannel.class)
                //指定InetSocketAddress，服务器监听此端口
                .localAddress("127.0.0.1", port)
                //设置childHandler执行所有的连接请求
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        channel.pipeline().addLast(new EchoServerHandel());
                    }
                });
        try {
            //调用ServerBootstrap.bind()方法来绑定服务器
            ChannelFuture future = bootstrap.bind().sync();
            System.out.println(EchoServer.class.getName() + " started and listen on " + future.channel().localAddress());
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new EchoServer(8080).startServer();
    }
}
