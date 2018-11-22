package cn.sh.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class Test {

    public static void main(String[] args) throws UnsupportedEncodingException {
//        duplicate();
//        copy();
//        slice();
        nioByteBuffer();
    }

    public static  void duplicate() {
        ByteBuf byteBuf = Unpooled.copiedBuffer("时间肯定会时间按客户登记卡萨电话卡黄金客户均可", CharsetUtil.UTF_8);
        byteBuf.readBytes(3);
        ByteBuf duplicate = byteBuf.duplicate();
        duplicate.writeBytes("圣诞节".getBytes());
        System.out.println(byteBuf.readerIndex());
        System.out.println(duplicate.readerIndex());
        duplicate.setBytes(3, "跑".getBytes());
        System.out.println(byteBuf.toString(CharsetUtil.UTF_8));
        byteBuf.readBytes(3);
        System.out.println(byteBuf.readerIndex());
        System.out.println(duplicate.readerIndex());
        System.out.println(duplicate.toString(CharsetUtil.UTF_8));
    }

    public static void copy() {
        ByteBuf byteBuf = Unpooled.copiedBuffer("时间肯定会时间按客户登记卡萨电话卡黄金客户均可", CharsetUtil.UTF_8);
        byteBuf.readBytes(6);
        ByteBuf copy = byteBuf.copy();
        System.out.println(byteBuf.readerIndex());
        System.out.println(copy.readerIndex());
        copy.setBytes(0, "否".getBytes());
        System.out.println(byteBuf.toString(CharsetUtil.UTF_8));
        System.out.println(copy.toString(CharsetUtil.UTF_8));
        copy.readBytes(3);
        copy.writeBytes("哈".getBytes());
        System.out.println(byteBuf.readerIndex());
        System.out.println(copy.readerIndex());
        System.out.println(byteBuf.toString(CharsetUtil.UTF_8));
        System.out.println(copy.toString(CharsetUtil.UTF_8));
    }

    public static void slice() {
        ByteBuf byteBuf = Unpooled.copiedBuffer("时间肯定会时间按客户登记卡萨电话卡黄金客户均可", CharsetUtil.UTF_8);
        byteBuf.readBytes(3);
        ByteBuf slice = byteBuf.slice();
        System.out.println(byteBuf.readerIndex());
        System.out.println(slice.readerIndex());
        byteBuf.writeBytes("收到货数据库".getBytes());
        System.out.println(byteBuf.toString(CharsetUtil.UTF_8));
        System.out.println(slice.toString(CharsetUtil.UTF_8));
        slice.setBytes(0, "时".getBytes());
        System.out.println(byteBuf.toString(CharsetUtil.UTF_8));
        System.out.println(slice.toString(CharsetUtil.UTF_8));
    }

    public static void nioByteBuffer() throws UnsupportedEncodingException {
        ByteBuf byteBuf = Unpooled.copiedBuffer("时间肯定会时间按客户登记卡萨电话卡黄金客户均可", CharsetUtil.UTF_8);
        byteBuf.readBytes(3);
        ByteBuffer byteBuffer = byteBuf.nioBuffer();
//        byteBuffer.put("哈".getBytes(), 0, "哈".getBytes().length);
//        byteBuf.setBytes(3, "哈".getBytes());
        byteBuf.writeBytes("哈哈哈哈哈".getBytes());
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        System.out.println(byteBuf.toString(CharsetUtil.UTF_8));
        System.out.println(new String(bytes, "UTF-8"));
    }
}
