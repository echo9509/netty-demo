package cn.sh.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

public class Test {

    public static void main(String[] args) {
        duplicate();
    }

    public static  void duplicate() {
        ByteBuf byteBuf = Unpooled.copiedBuffer("时间肯定会时间按客户登记卡萨电话卡黄金客户均可", CharsetUtil.UTF_8);
        byteBuf.readBytes(3);
        ByteBuf duplicate = byteBuf.duplicate();
        System.out.println(byteBuf.readerIndex());
        System.out.println(duplicate.readerIndex());
        duplicate.setBytes(3, "跑".getBytes());
        System.out.println(byteBuf.toString(CharsetUtil.UTF_8));
        byteBuf.readBytes(3);
        System.out.println(byteBuf.readerIndex());
        System.out.println(duplicate.readerIndex());
        System.out.println(duplicate.readBytes(3).toString(CharsetUtil.UTF_8));
    }
}
