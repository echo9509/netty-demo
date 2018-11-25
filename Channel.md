# Channel和Unsafe
Netty提供了自己的Channel和其子类实现，用于异步I/O操作和其他相关操作。

Unsafe是一个内部接口，聚合在Channel中协助进行网络读写相关的操作。Unsafe是Channel的内部辅助类，不应该被Netty框架的上层使用者调用。

# Channel功能说明
Channel是Netty网络操作抽象类，聚合了一组功能，包括但不限于网络的读、写，客户端发起连接、主动关闭连接，链路关闭，获取通信双方的网络地址等。
它也包含了Netty框架相关的一些功能，包括获取该Channel的EventLoop，获取缓冲分配器ByteBufAllocator和pipeline等。

## Channel工作原理
JDK NIO Channel的缺点：
1. JDK的SocketChannel和ServerSocketChannel没有统一的Channel接口供业务开发者使用
2. JDK的SocketChannel和ServerSocketChannel主要职责就是网络I/O操作，由于他们的是SPI类接口，具体的实现由虚拟机厂家决定，所以扩展不方便。
3. Netty的Channel需要能够跟Netty的整体架构融合在一起，例如I/O模型、基于ChannelPipeline的定制模型。以及基于元数据描述配置化的TCP参数等，
上述JDK的SocketChannel和ServerSocketChannel都没有提供，需要重新封装

Netty Channel设计原理：
1. 在Channel接口层，采用Facade模式进行统一封装，将网络I/O操作、网络I/O相关联的其他操作封装起来，统一对外提供。
2. Channel接口的定义尽量大而全，为SocketChannel和ServerSocketChannel提供统一的视图，由不同子类实现不同的功能，公共功能在抽象父类实现，
最大程度上实现功能和接口的重用
3. 具体实现采用聚合而非包含的方式，将相关的功能类聚合在Channel中，由Channel统一负责分配和调度，功能实现更加灵活。

### Channel功能介绍
#### 网络I/O操作
1. Channel read()：从当前的Channel中读取数据到第一个inbound缓冲区，如果数据被成功读取，触发ChannelHandler.channelRead(ChannelHandler
Context context, ChannelPromise promise)事件，读取操作API调用完成之后，紧接着会触发ChannelHandler.channelReadComplete事件
2. ChannelFuture write(Object msg)：请求将当前的msg通过ChannelPipeline写入到目标Channel中。write操作只是将消息存入到消息发送环形数
组中，并没有真正被发送，只有调用flush操作才会被写入Channel中，发送给对方。
3. ChannelFuture write(Object msg, ChannelPromise promise)：功能与write(Object msg)相同，但是携带了ChannelPromise参数负责设置
写入操作的结果
4. ChannelFuture writeAndFlush(Object msg, ChannelPromise promise)：与方法3类似，不同之处在于它会将消息写入到Channel中发送，等价
于单独调用write和flush。
5. ChannelFuture writeAndFlush(Object msg)：功能等同于4，但是没有携带ChannelPromise参数
6. Channel flush()：将之前写入消息发送环形数组中的消息全部写入目标Channel中，发送给通信对方。
7. ChannelFuture close(ChannelPromise promise)：主动关闭当前连接，通过ChannelPromise设置操作结果并进行结果通知，无论操作是否成功，
都可以通过ChannelPromise获取操作结果。该操作会级联触发ChannelPipeline中所有ChannelHandler的ChannelHandler.close(ChannelHandler
Context context, ChannelPromise promise)事件
8. ChannelFuture disconnect(ChannelPromise promise)：请求断开与远程通信对端的连接并使用ChannelPromise来获取操作结果的通知消息。该
方法会级联触发ChannelHandler.disconnect(ChannelHandlerContext context, ChannelPromise promise)事件
9. ChannelFuture connect(SocketAddress remoteAddress)：客户端使用指定的服务端地址remoteAddress发起连接请求，如果连接因为应答超时
而失败，ChannelFuture中的操作结果就是ConnectTimeoutException，如果连接被拒绝，操作结果是ConnectException。该方法会级联触发
ChannelHandler.connect(ChannelHandlerContext context, SocketAddress remoteAddress, SocketAddress localAddress, 
ChannelPromise promise)事件。
10. ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress)：该功能与9相似，唯一不同的是先绑定指定的本地
地址localAddress，然后再连接服务端
11. ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise)：该功能与9相似，唯一不同的是携带了ChannelPromise
参数用来写入操作结果
12. ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise)：与11功能类似，
唯一不同就是绑定了本地地址
13. ChannelFuture bind(SocketAddress localAddress)：绑定指定的本地Socket地址localAddress，该方法会级联触发ChannelHandler.bind
(ChannelHandlerContext context, ChannelPromise promise)事件
14. ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise)：与13功能类似，携带了ChannelPromise参数来写入操作结果
15. ChannelConfig config()：获取当前Channel的配置信息，例如CONNECT_TIMEOUT_MILLIS
16. boolean isOpen()：判断当前Channel是否已经打开
17. boolean isRegistered()：判断当前Channel是否已经注册到EventLoop上
18. boolean isActive()：判断当前Channel是否已经处于激活状态
19. ChannelMetadata metadata()：获取当前Channel的元数据描述信息，包括TCP参数配置等。
20. SocketAddress localAddress()：获取当前Channel的本地绑定地址
21. SocketAddress remoteAddress()：获取当前Channel通信的远程Socket地址

#### 其他API
1. EventLoop eventLoop()：获取Channel注册的EventLoop。Channel需要注册到EventLoop的多路复用器上，用来处理I/O事件，EventLoop本质上
就是处理网络读写事件的Reactor线程。在Netty中，EventLoop不仅仅用来处理网络事件，也可以用来执行定时任务和用户自定义NioTask等任务。
2. Channel parent()：对于服务端Channel而言，父Channel为空；对于客户端Channel，它的父Channel就是创建它的ServerSocketChannel
3. ChannelId id()：返回ChannelId对象，ChannelId是Channel的唯一标识

ChannelId的可能生成策略如下：
1. 机器的MAC地址等可以代表全局唯一的信息
2. 当前的进程ID
3. 当前系统时间的毫秒——System.currentTimeMillis()
4. 当前系统时间纳秒数——System.nanoTime()
5. 32位的随机整形数
6. 32位自增的序列数

## Channel源码
### Channel继承关系类图
服务端NioServerSocketChannel继承关系图
![FF7QMQ.png](https://s1.ax1x.com/2018/11/24/FF7QMQ.png)

客户端NioSocketChannel继承关系图
![FF7URU.png](https://s1.ax1x.com/2018/11/24/FF7URU.png)

### AbstractChannel源码
#### 成员变量定义
![FF7ci6.png](https://s1.ax1x.com/2018/11/24/FF7ci6.png)
1. 首先定义了5个静态全局异常
2. Channel parent：代表父类Channel
3. ChannelId id：采用默认方式生成的全局唯一ID
4. Unsafe unsafe：Unsafe实例
5. DefaultChannelPipeline pipeline：当前Channel对应的DefaultChannelPipeline
6. EventLoop eventLoop：当前Channel注册的EventLoop等一系列

通过变量定义可以看出，AbstractChannel聚合了所有Channel使用到的能力对象，由AbstractChannel提供初始化和统一封装，如果功能和子类强相关，则
定义成抽象方法由子类具体实现。

#### 核心API
Netty基于事件驱动，当Channel进行I/O操作时会产生对应的I/O事件，然后驱动事件在ChannelPipeline中传播，由对应的ChannelHandler对事件进行拦截
和处理，不关心的事件可以直接忽略。

网络I/O操作直接调用DefaultChannelPipeline的相关方法，由DefaultChannelPipeline中对应的ChannelHandler进行具体的逻辑处理。

AbstractChannel提供了一些公共API的具体实现，例如localAddress()和remoteAddress()，下面看一下remoteAddress的源码：
```java
    @Override
    public SocketAddress remoteAddress() {
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress = unsafe().remoteAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }
```
首先从缓存的成员变量中获取，如果第一次调用为空，需要通过unsafe的remoteAddress获取，它是个抽象方法，具体由对应的Channel子类实现。

### AbstractNioChannel源码
#### 成员变量定义
![FFLmwj.png](https://s1.ax1x.com/2018/11/24/FFLmwj.png)
1. 定义了一个DO_CLOSE_CLOSED_CHANNEL_EXCEPTION静态全局异常
2. SelectableChannel ch：由于NIO Channel、NioSocketChannel和NioServerSocketChannel需要公用，所以定义了一个SocketChannel和
ServerSocketChannel的公共父类SelectableChannel，用于设置SelectableChannel参数和进行I/O操作。
3. int readInterestOp：代表了JDK SelectionKey的OP_READ
4. volatile SelectionKey selectionKey：该SelectionKey是Channel注册到EventLoop后返回的选择键。由于Channel会面临多个业务线程的并发
写操作，当SelectionKey由SelectionKey修改以后，为了能让其他业务线程感知到变化，所以需要使用volatile保证修改的可见性。
5. ChannelPromise connectPromise：代表连接操作结果
6. ScheduledFuture<?> connectTimeoutFuture：连接超时定时器
7. SocketAddress requestedRemoteAddress：请求的远程通信地址信息

#### 核心源码
AbstractNioChannel注册源码
```java
    @Override
    protected void doRegister() throws Exception {
        boolean selected = false;
        for (;;) {
            try {
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                return;
            } catch (CancelledKeyException e) {
                if (!selected) {
                    // Force the Selector to select now as the "canceled" SelectionKey may still be
                    // cached and not removed because no Select.select(..) operation was called yet.
                    eventLoop().selectNow();
                    selected = true;
                } else {
                    // We forced a select operation on the selector before but the SelectionKey is still cached
                    // for whatever reason. JDK bug ?
                    throw e;
                }
            }
        }
    }
```
首先定义一个boolean类型的局部变量selected来标识注册操作是否成功，调用SelectableChannel的register方法，将当前的Channel注册到EventLoop
的多路复用器上，SelectableChannel的注册方法定义如下：
```java
public abstract SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException;
```
注册Channel的时候需要指定监听的网络操作位来表示Channel对哪几类网络事件感兴趣，具体的定义如下：
1. public static final int OP_READ = 1 << 0： 读操作位
2. public static final int OP_WRITE = 1 << 2：写操作位
3. public static final int OP_CONNECT = 1 << 3：客户端连接服务端操作位
4. public static final int OP_ACCEPT = 1 << 4：服务端接受客户端连接操作位

AbstractNioChannel注册的是0，说明对任何事件不感兴趣，仅仅完成注册操作。注册的时候可以指定附件，后续Channel接收到网络事件通知时可以从
SelectionKey中重新获取之前的附件进行处理。如果注册Channel成功，则返回SelectionKey，通过SelectionKey可以从多路复用器中获取Channel对象。

如果当前注册返回的SelectionKey已经被取消，则抛出CancelledKeyException异常，捕获该异常进行处理。如果是第一次处理该异常，调用多路复用器的
selectNow()方法将已经取消的selectionKey从多路复用器中删除掉。操作成功之后，将selected置为true，说明之前失效的selectionKey已经被删除掉。
继续发起下一次注册操作，如果成功则退出，如果仍然发生CancelledKeyException异常，说明我们无法删除已经被取消的selectionKey，发生这种问题，
直接抛出CancelledKeyException异常到上层进行统一处理。

下面看一下准备处理读操作之前需要设置网络操作位为读的代码：
```java
    @Override
    protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }
```
获取当前的SelectionKey进行判断，如果可用说明Channel当前状态正常，则可以进行正常的操作位修改。先将等待读设置为true，将SelectionKey当前的
操作位与读操作位按位于操作，如果等于0，说明目前并没有设置读操作位，通过interestOps | readInterestOp设置读操作位，最后调用selectionKey的
interestOps方法重新设置通道的网络操作位，这样就可以监听网络的读事件。

### AbstractNioByteChannel
#### 成员变量定义
private final Runnable flushTask：负责继续写半包消息

#### API
看下doWrite(ChannelOutboundBuffer in)的源码：
```java
    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = config().getWriteSpinCount();
        do {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }
            writeSpinCount -= doWriteInternal(in, msg);
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);
    }
```
首先从消息发送环形数组中弹出一个消息，判断该消息是否为空，如果为空，说明所有的消息都已经发送完成，清除半包标识，退出循环（该循环的次数默认最
多16次），设置半包消息有最大处理次数的原因是当循环发送的时候，I/O线程会一直进行写操作，此时I/O线程无法处理其他的I/O操作，例如读新的消息或
执行定时任务和NioTask等，如果网络I/O阻塞或者对方接受消息太慢，可能会导致线程假死。看一下清除半包标识clearOpWrite()的逻辑代码：
```java
    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        }
    }
```
首先获取当前的SelectionKey，如果当前的SelectionKey已被取消或者无效，直接返回。如果有效，则获取当前的监控的网络操作位，判断当前的网络操作
位是否监听写事件，如果正在监听，则取消对写事件的监听。

如果发送的消息不为空，则继续对消息进行处理，源码如下：
```java
    private int doWriteInternal(ChannelOutboundBuffer in, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.isReadable()) {
                in.remove();
                return 0;
            }

            final int localFlushedAmount = doWriteBytes(buf);
            if (localFlushedAmount > 0) {
                in.progress(localFlushedAmount);
                if (!buf.isReadable()) {
                    in.remove();
                }
                return 1;
            }
        } else if (msg instanceof FileRegion) {
            FileRegion region = (FileRegion) msg;
            if (region.transferred() >= region.count()) {
                in.remove();
                return 0;
            }

            long localFlushedAmount = doWriteFileRegion(region);
            if (localFlushedAmount > 0) {
                in.progress(localFlushedAmount);
                if (region.transferred() >= region.count()) {
                    in.remove();
                }
                return 1;
            }
        } else {
            // Should not reach here.
            throw new Error();
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }
```
首先判断消息的类型是否是ByteBuf，如果是进行强制转换，判断ByteBuf是否可读，如果不可读，将该消息从消息循环数组中删除，继续循环处理其他消息。
如果可读，则由具体的实现子类完成将ByeBuf写入到Channel中，并返回写入的字节数。如果返回的字节数小于等于0，则返回整形的最大数值。如果返回的
写入字节数大于0，设置该消息的处理的进度，然后再判断该消息是否可读，如果不可读，就把该消息移除，返回1。