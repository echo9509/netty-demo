package cn.sh.echoserver;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

import java.util.Scanner;

/**
 * @author sh
 */
public class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    Scanner sc = new Scanner( System.in);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        writeMessage(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf msg) throws Exception {
        byte[] bytes = new byte[msg.readableBytes()];
        msg.readBytes(bytes);
        System.out.println("Echo client received:" + new String(bytes));
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        writeMessage(ctx);
    }

    private void writeMessage(ChannelHandlerContext ctx) {
        String msg = sc.next();
        ctx.writeAndFlush(Unpooled.copiedBuffer(msg.getBytes()));
    }
}
