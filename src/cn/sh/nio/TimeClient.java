package cn.sh.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @author sh
 */
public class TimeClient implements Runnable {

    private Selector selector;

    private volatile boolean stop;

    public TimeClient() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            doConnect();
            while (!stop) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    handleKey(key);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (selector != null) {
                try {
                    selector.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleKey(SelectionKey key) throws IOException {
        if (!key.isValid()) {
            return;
        }
        SocketChannel socketChannel = (SocketChannel) key.channel();
        if (key.isConnectable() && socketChannel.finishConnect()) {
            socketChannel.register(selector, SelectionKey.OP_READ);
            System.out.println("已连接到服务器");
            String order = "QUERY TIME ORDER";
            ByteBuffer byteBuffer = ByteBuffer.allocate(order.getBytes().length);
            byteBuffer.put(order.getBytes());
            socketChannel.write(byteBuffer);
            byteBuffer.flip();
            return;
        }
        if (key.isReadable()) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            int read = socketChannel.read(byteBuffer);
            if (read > 0) {
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                System.out.println("当前时间:" + new String(bytes, "UTF-8"));
                stop = true;
            } else if (read < 0) {
                key.cancel();
                socketChannel.close();
            }
        }
    }

    private void doConnect() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
        socketChannel.connect(new InetSocketAddress(8080));
    }
}
