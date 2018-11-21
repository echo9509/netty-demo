package cn.sh.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

/**
 * @author sh
 */
public class TimerServer implements Runnable {

    private ServerSocketChannel serverSocketChannel;

    private Selector selector;

    private volatile boolean stop;

    public TimerServer() {
        try {
            doAccept();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (!stop) {
                selector.select();
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    handlerKey(key);
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

    private void handlerKey(SelectionKey key) throws IOException {
        if (!key.isValid()) {
            return;
        }
        if (key.isAcceptable()) {
            ServerSocketChannel channel = (ServerSocketChannel) key.channel();
            System.out.println("客户端已连接");
            SocketChannel socketChannel = channel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
        }
        if (key.isReadable()) {
            SocketChannel socketChannel = (SocketChannel) key.channel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            int readByte = socketChannel.read(byteBuffer);
            if (readByte > 0) {
                int remaining = byteBuffer.remaining();
                byte[] bytes = new byte[remaining];
                byteBuffer.get(bytes);
                String order = new String(bytes, "UTF-8");
                System.out.println("receive order:" + order);
                String currentTime = "QUERY TIME ORDER".equals(order) ? new Date().toString() : "BAD ORDER";
                doWrite(socketChannel, currentTime);
            } else if (readByte < 0){
                key.cancel();
                socketChannel.close();
            }
        }
    }

    private void doWrite(SocketChannel socketChannel, String currentTime) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(currentTime.getBytes().length);
        byteBuffer.put(currentTime.getBytes());
        byteBuffer.flip();
        socketChannel.write(byteBuffer);
    }

    private void doAccept() throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(8080));
        serverSocketChannel.configureBlocking(false);
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void stop() {
        stop = true;
    }
}
