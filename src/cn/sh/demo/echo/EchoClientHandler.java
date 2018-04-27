package cn.sh.demo.echo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

/**
 * @author sh
 * @ChannelHandler.Sharable 标记该类的示例可以被多个Channel共享
 */
@ChannelHandler.Sharable
public class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        //当一个连接被服务器接受并建立后，发送一条消息
        ctx.writeAndFlush(Unpooled.copiedBuffer("Hello, Netty", CharsetUtil.UTF_8));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) {
        //记录客户端接收到服务器的消息
        System.out.println("Client received:" + byteBuf.toString(CharsetUtil.UTF_8));
    }

    /**
     * 在发生异常时，记录错误并关闭Channel
     * @param ctx
     * @param cause
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}