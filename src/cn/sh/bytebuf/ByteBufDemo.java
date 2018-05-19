package cn.sh.bytebuf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

/**
 * @author sh
 */
public class ByteBufDemo {

    public static void main(String[] args) {
        ByteBuf heapBuf = Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8);
        System.out.println(heapBuf.capacity());
        for (int i = 0 ; i < heapBuf.capacity(); i++) {
            System.out.println((char) heapBuf.getByte(i));
        }
        System.out.println(heapBuf.readerIndex());
    }
}
