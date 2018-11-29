Netty的ChannelPipeline和ChannelHandler机制类似于Servlet和Filter过滤器，主要是为了方便事件的拦截和用户业务逻辑的定制。

Netty的Channel过滤器实现原理与Serverlet Filter机制一致，它将Channel的数据管道抽象为ChannelPipeline，消息在ChannelPipeline中流动和传递。
ChannelPipeline持有I/O事件拦截器ChannelHandler的链表，由ChannelHandler对I/O事件进行拦截和处理，可以方便地通过新增和删除ChannelHandler
来实现不同的业务逻辑定制，不需要对已有的ChannelHandler进行修改，能够实现对修改封闭和对扩展的支持。

# ChannelPipeline
ChannelPipeline是ChannelHandler的容器，它负责ChannelHandler的管理和事件拦截与调度。
## ChannelPipeline事件处理
ChannelPipeline的ChannelHandler链拦截和处理过程：
1. 底层的SocketChannel read()方法读取ByteBuf，触发ChannelRead事件，由I/O线程NioEventLoop调用ChannelPipeline的fireChannelRead(Object msg)
方法，将消息ByteBuf传输到ChannelPipeline中
3. 消息依次被HeadHandler、ChannelHandler1、ChannelHandler2、……TailHandler拦截处理，在这个过程中，任何ChannelHandler都可以中断当前的流程，
结束消息的传递
3. 调用ChannelHandlerContext的write方法会发送消息，消息从TailHandler开始，经过ChannelHandlerN……ChannelHandler1、HeadHandler，最终
被添加到消息缓冲区中等待刷新和发送，在此过程中也可以中断消息的传递，例如当编码失败时，就需要中断流程，构造异常的Future返回。

Netty中的事件分为inbound事件和outbound事件。inbound事件通常由I/O线程触发，例如TCP链路建立事件、链路关闭事件、读事件、异常通知事件等。

触发inbound事件的方法如下：
1. ChannelHandlerContext.fireChannelRegistered()：Channel注册事件
2. ChannelHandlerContext.fireChannelActive()：TCP连接建立成功，Channel激活事件
3. ChannelHandlerContext.fireChannelRead(Object msg)：读事件
4. ChannelHandlerContext.fireChannelReadComplete()：读操作完成通知事件
5. ChannelHandlerContext.fireExceptionCaught(Throwable cause)：异常通知事件
5. ChannelHandlerContext.fireUserEventTriggered(Object evt)：用户自定义事件
6. ChannelHandlerContext.fireChannelWritabilityChanged()：Channel的可写状态变化通知事件
7. ChannelHandlerContext.fireChannelInactive()：TCP关闭连接，链路不可用通知事件

Outbound事件通常是由用户主动发起的网络I/O操作，例如用户发起的连接操作、绑定操作、消息发送等操作。

触发outbound事件的方法如下：
1. ChannelHandlerContext.bind(SocketAddress localAddress, ChannelPromise promise)：绑定本地地址事件
2. ChannelHandlerContext.connect(SocketAddress remoteAddress, ChannelPromise promise)：连接服务端事件
3. ChannelHandlerContext.flush()：刷新事件
4. ChannelHandlerContext.read()：读事件
5. ChannelHandlerContext.disconnect(ChannelPromise promise)：断开连接事件
6. ChannelHandlerContext.close(ChannelPromise promise)：关闭当前Channel事件

## 构建ChannelPipeline
事实上，用户不需要自己创建ChannelPipeline，因为使用ServerBootstrap或者Bootstrap启动服务端或者客户端时，Netty会为每个Channel连接创建一个
独立的pipeline。对于使用者来说，只需要将自定义的拦截器加入到pipeline中。

对于编解码的ChannelHandler，存在先后顺序，例如MessageToMessageDecoder，在它之前往往需要有ByteToMessageDecoder将ByteBuf解码为对象，
然后对对象做二次解码得到最终的POJO对象。

ChannelPipeline支持指定位置添加和删除ChannelHandler

## ChannelPipeline主要特性
ChannelPipeline支持运行时动态添加或者删除ChannelHandler，在某些场景下这个特性非常实用。例如当业务高峰期时需要对系统做拥塞保护时，就可以
根据当前的系统时间进行判断，如果处于业务高峰期，则动态地将系统拥塞保护ChannelHandler添加到当前的ChannelPipeline，当高峰过去时，就可以动
态删除拥塞保护ChannelHandler。

ChannelPipeline是线程安全的，这意味着N个业务线程可以并发地操作ChannelPipeline而不存在多线程并发问题。但是ChannelHandler不是线程安全的，
意味着尽管ChannelPipeline是线程安全，但用户仍然需要自己保证ChannelHandler的线程安全。

# ChannelPipeline源码
ChannelPipeline实际上是一个ChannelHandler的容器，内部维护了一个ChannelHandler的链表和迭代器，可以方便地实现ChannelHandler查找、添加、
替换和删除

## ChannelPipeline类继承关系图

![FeGQ1S.png](https://s1.ax1x.com/2018/11/29/FeGQ1S.png)

## ChannelPipeline对ChannelHandler的管理
ChannelPipeline是ChannelHandler的管理容器，负责ChannelHandler的查询、添加、替换和删除。

```java
    @Override
    public final ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        return addBefore(null, baseName, name, handler);
    }

    @Override
    public final ChannelPipeline addBefore(
            EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        final AbstractChannelHandlerContext ctx;
        synchronized (this) {
            checkMultiplicity(handler);
            name = filterName(name, handler);
            ctx = getContextOrDie(baseName);

            newCtx = newContext(group, name, handler);

            addBefore0(ctx, newCtx);

            // If the registered is false it means that the channel was not registered on an eventloop yet.
            // In this case we add the context to the pipeline and add a task that will call
            // ChannelHandler.handlerAdded(...) once the channel is registered.
            if (!registered) {
                newCtx.setAddPending();
                callHandlerCallbackLater(newCtx, true);
                return this;
            }

            EventExecutor executor = newCtx.executor();
            if (!executor.inEventLoop()) {
                newCtx.setAddPending();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        callHandlerAdded0(newCtx);
                    }
                });
                return this;
            }
        }
        callHandlerAdded0(newCtx);
        return this;
    }
```
由于ChannelPipeline支持运行期动态修改，因此存在两种潜在的多线程并发访问场景：
- I/O线程和用户业务线程的并发访问
- 用户多个线程之间的并发访问

Netty在此处使用了synchronized关键字，保证同步块内的所有操作的原子性。首先需要对添加的ChannelHandler做重复性校验，校验代码如下：
```java
    private static void checkMultiplicity(ChannelHandler handler) {
        if (handler instanceof ChannelHandlerAdapter) {
            ChannelHandlerAdapter h = (ChannelHandlerAdapter) handler;
            if (!h.isSharable() && h.added) {
                throw new ChannelPipelineException(
                        h.getClass().getName() +
                        " is not a @Sharable handler, so can't be added or removed multiple times.");
            }
            h.added = true;
        }
    }
```
如果ChannelHandler不是可以在多个ChannelPipeline中共享的，并且已经被添加到ChannelPipeline中，则抛出ChannelPipelineException异常。

然后后对新增的ChannelHandler名进行重复性校验，在校验之前，如果没有传递名称，会自动生成一个名称，如果已经有同名的ChannelHandler存在，
则不允许覆盖，会抛出IllegalArgumentException异常。

接着baseName获取到对应的ChannelHandlerContext，ChannelPipeline维护了第一个ChannelHandlerContext和最后一个ChannelHandlerContext，
ChannelHandlerContext又维护了它的前后ChannelHandlerContext。getContextOrDie最终会调取DefaultChannelPipeline的context0(String name)
方法，方法如下：
```java
    private AbstractChannelHandlerContext context0(String name) {
        AbstractChannelHandlerContext context = head.next;
        while (context != tail) {
            if (context.name().equals(name)) {
                return context;
            }
            context = context.next;
        }
        return null;
    }
```

然后调用newContext方法为新添加的ChannelHandler生成ChannelHandlerContext，并将其添加到合适的位置。加入成功，发送新增ChannelHandlerContext
通知，也就是回调ChannelHandler.handlerAdded方法。

## ChannelPipeline的outbound事件
ChannelPipeline本身并不直接进行I/O操作，最终都是由Unsafe和Channel来实现真正的I/O操作。Pipeline负责将I/O事件通过TailHandler进行调度和传播，
最终调用Unsafe的I/O方法进行I/O操作。
```java
    @Override
    public final ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, promise);
    }
```
他会直接调用TailHandler的connect方法，最终会调用到HeadHandler的connect方法，代码如下所示：
```java
        @Override
        public void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) throws Exception {
            unsafe.connect(remoteAddress, localAddress, promise);
        }
```