# Netty线程模型
## Reactor单线程模型
Reactor单线程模型，是指所有的I/O操作都在同一个NIO线程上面完成。NIO线程的主要职责如下：
- 作为NIO服务端，接受客户端的TCP连接
- 作为NIO客户端，向服务端发起TCP连接
- 读取通信对端的请求或者应答消息
- 向通信对端发送消息请求或者应答消息

![FudRNF.png](https://s1.ax1x.com/2018/12/02/FudRNF.png)

由于Reactor模式使用的是异步非阻塞I/O，所有的I/O操作都不会导致阻塞，理论上一个线程可以独立处理所有I/O相关的操作。例如：通过Acceptor
类接受客户端的TCP连接请求消息，当链路建立成功之后，通过Dispatch将对应的ByteBuffer派发到指定的Handler上，进行消息解码。用户线程消息
解码后通过NIO线程将消息发送给客户端。

高并发不适合的原因：
- 一个NIO线程同时处理成百上千的链路，性能上无法支撑，即使NIO线程的CPU负荷达到100%，也无法满足海量消息的编解码、读取和发送。
- 当NIO线程负载过重之后，处理速度将变慢，这会导致大量客户端连接超时，超时之后往往会进行重发，这更加重了NIO线程的负载，最终会导致大量
消息挤压和处理超时，成为系统的瓶颈
- 可靠性问题：一旦NIO线程意外跑飞或进入死循环，会导致整个通信模块不可用，不能接受和处理外部消息，造成接点故障。

## Reactor多线程模型

![FuBh9g.png](https://s1.ax1x.com/2018/12/02/FuBh9g.png)

Reactor多线程的特点：
- 有专门一个NIO线程——Acceptor线程用于监听服务端，接收客户端的TCP连接请求
- 网络I/O操作——读、写等由一个NIO线程池负责，线程池可以采用标准的JDK线程池实现，它包含一个任务队列和N个可用的线程，由这些NIO线程负责消息
的读取、编解码和发送。
- 一个NIO线程可以同时处理N条链路，但是一个链路只对应一个NIO线程，防止发生并发操作问题。

缺点：例如并发百万客户端连接，或者服务端需要对客户端握手进行安全认证，但是认证本身非常损耗性能，这类场景下，单独一个Acceptor线程可能会存在
性能不足的问题

## 主从Reactor多线程模型

![FurmJU.png](https://s1.ax1x.com/2018/12/02/FurmJU.png)

主从Reactor多线程模型特点：
- 服务端用于接受客户端连接的不再是一个单独的NIO线程，而是一个独立的NIO线程池。
- Acceptor接收到客户端TCP连接请求并处理完成后(可能包含接入认证等)，将新创建的SocketChannel注册到I/O线程池(从Reactor线程池)的某个线程
上，由它负责SocketChannel的读写和编解码工作。
- Acceptor线程池仅仅用于客户端的登录、握手和安全认证，一旦链路建立成功，就将链路注册到后端从Reactor线程池的I/O线程上，由I/O线程负责后续
的I/O操作。

## Netty线程模型
Netty的线程模型不是一成不变的，它实际取决于用户的启动参数配置。通过设置不同的启动参数，Netty可以同时支持Reactor单线程模型、多线程模型和
主从Reactor多线程模型。

Netty用于接收客户端请求的线程池职责如下：
- 接收客户端TCP连接，初始化Channel参数
- 将链路状态变更事件通知给ChannelPipeline

Netty处理I/O操作的Reactor线程池职责如下：
- 异步读取通信对端的数据报，发送读事件到ChannelPipeline
- 异步发送消息到通信对端，调用ChannelPipeline的消息发送接口
- 执行系统调用Task
- 执行定时任务Task，例如链路空闲状态监测定时任务

Netty的NioEventLoop读取到消息之后，直接调用ChannelPipeline的fireChannelRead。只要用户不主动切换线程，一直都是由NioEventLoop调用用户
的Handler，期间不进行线程切换。这种串行化的处理方式避免了多线程操作导致的锁竞争。

## 最佳实践
- 创建两个NioEventLoopGroup，用于逻辑隔离NIO Acceptor和NIO I/O线程
- 尽量不要在ChannelHandler中启动用户线程（解码后用于将POJO消息派发到后端业务线程除外）
- 解码要放在NIO线程调用的解码Handler中进行，不要切换到用户线程中完成消息的解码
- 如果业务逻辑操作非常简单，没有复杂的业务逻辑计算，没有可能会导致线程被阻塞的磁盘操作、数据库操作、网络操作等，可以直接在NIO线程上完成
业务逻辑编排，不需要切换到用户线程
- 如果业务逻辑处理复杂，不要在NIO线程上完成，建议将解码后的POJO消息封装成Task，派发到业务线程池由业务线程执行，以保证NIO线程尽快释放，
处理其他的I/O操作

线程池数量的计算公式：
- 线程数量 = (线程总时间/瓶颈资源时间) * 瓶颈资源的线程并行数
- QPS = 1000/线程总时间 * 线程数

# NioEventLoop源码
## NioEventLoop设计原理
Netty的NioEventLoop并不是一个纯粹的I/O线程，它除了负责I/O的读写之外，还兼顾处理以下两类任务。
- 系统Task：通过调用NioEventLoop的execute(Runnable task)方法实现，Netty有很多系统Task，创建它们的主要原因是：当I/O线程和用户线程同时
操作网络资源时，为了防止并发操作导致的锁竞争，将用户线程的操作封装成Task放入消息队列中，由I/O线程负责执行，这样就实现了局部的无锁化
- 定时任务：通过调用NioEventLoop的schedule(Runnable command, long delay, TimeUnit unit)方法实现。

## NioEventLoop继承关系

![Fu4yRI.png](https://s1.ax1x.com/2018/12/02/Fu4yRI.png)

## NioEventLoop
