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