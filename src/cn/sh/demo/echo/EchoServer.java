package cn.sh.demo.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

public class EchoServer {

    private final int port;

    public EchoServer(int port) {
        this.port = port;
    }

    public void startServer() throws InterruptedException {
        EchoServerHandler serverHandler = new EchoServerHandler();
        //创建EventLoopGroup
        EventLoopGroup group = new NioEventLoopGroup();
        //创建ServerBootstrap
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(group)
                //指定所使用的NIO传输Channel
                .channel(NioServerSocketChannel.class)
                //使用指定的端口套接字
                .localAddress(new InetSocketAddress(port))
                //添加一个EchoServerHandler到子Channel的ChannelPipeline
                .childHandler(new ChannelInitializer<NioServerSocketChannel>() {
                    @Override
                    protected void initChannel(NioServerSocketChannel channel) throws Exception {
                        //此处由于EchoServerHandler被注解标注为@Shareble，所以我们总是使用相同的实例
                        channel.pipeline().addLast(serverHandler);
                    }
                });
        try {
            //异步的绑定服务器，调用sync()方法阻塞等待直到绑定完成
            ChannelFuture channelFuture = bootstrap.bind().sync();
            //获取Channel的CloseFuture，并且阻塞当前线程直到它完成
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //关闭EventLoopGroup，释放所有的资源
            group.shutdownGracefully().sync();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 1) {
            System.err.println("缺少参数");
            return;
        }
        //设置端口值
        int port = Integer.parseInt(args[0]);
        //启动Echo服务器
        new EchoServer(port).startServer();
    }
}
