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
因为NioEventLoop是一个Reactor线程，所以他肯定聚合了Selector多路复用器对象，代码如下：
```java
    /**
     * The NIO {@link Selector}.
     */
    private Selector selector;
    private Selector unwrappedSelector;
    private SelectedSelectionKeySet selectedKeys;

    private final SelectorProvider provider;
```
Selector的初始化直接调用Selector.open()方法就可以创建并打开一个新的Selector。Netty对Selector的selectedKeys进行了优化，用户可以通过
io.netty.noKeySetOptimization开关决定是否要启用该优化项，默认不打开该优化功能。
```java
    private SelectorTuple openSelector() {
        final Selector unwrappedSelector;
        try {
            unwrappedSelector = provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }

        if (DISABLE_KEYSET_OPTIMIZATION) {
            return new SelectorTuple(unwrappedSelector);
        }

        final SelectedSelectionKeySet selectedKeySet = new SelectedSelectionKeySet();

        Object maybeSelectorImplClass = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    return Class.forName(
                            "sun.nio.ch.SelectorImpl",
                            false,
                            PlatformDependent.getSystemClassLoader());
                } catch (Throwable cause) {
                    return cause;
                }
            }
        });

        if (!(maybeSelectorImplClass instanceof Class) ||
                // ensure the current selector implementation is what we can instrument.
                !((Class<?>) maybeSelectorImplClass).isAssignableFrom(unwrappedSelector.getClass())) {
            if (maybeSelectorImplClass instanceof Throwable) {
                Throwable t = (Throwable) maybeSelectorImplClass;
                logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, t);
            }
            return new SelectorTuple(unwrappedSelector);
        }

        final Class<?> selectorImplClass = (Class<?>) maybeSelectorImplClass;

        Object maybeException = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    Field selectedKeysField = selectorImplClass.getDeclaredField("selectedKeys");
                    Field publicSelectedKeysField = selectorImplClass.getDeclaredField("publicSelectedKeys");

                    Throwable cause = ReflectionUtil.trySetAccessible(selectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }
                    cause = ReflectionUtil.trySetAccessible(publicSelectedKeysField, true);
                    if (cause != null) {
                        return cause;
                    }

                    selectedKeysField.set(unwrappedSelector, selectedKeySet);
                    publicSelectedKeysField.set(unwrappedSelector, selectedKeySet);
                    return null;
                } catch (NoSuchFieldException e) {
                    return e;
                } catch (IllegalAccessException e) {
                    return e;
                }
            }
        });

        if (maybeException instanceof Exception) {
            selectedKeys = null;
            Exception e = (Exception) maybeException;
            logger.trace("failed to instrument a special java.util.Set into: {}", unwrappedSelector, e);
            return new SelectorTuple(unwrappedSelector);
        }
        selectedKeys = selectedKeySet;
        logger.trace("instrumented a special java.util.Set into: {}", unwrappedSelector);
        return new SelectorTuple(unwrappedSelector,
                                 new SelectedSelectionKeySetSelector(unwrappedSelector, selectedKeySet));
    }
```
如果没有开启优化，直接调用provider.openSelector()创建并打开多路复用器之后立即返回。

如果开启了优化功能，需要通过反射的方式从Selector实例中获取selectedKeys和publicSelectedKeys，通过反射的方式使用Netty构造的selectedKeys
的包装类SelectedSelectionKeySet将原有的selectedKeys替换掉。

下面看以下NioEventLoop的主要代码
```java
    @Override
    protected void run() {
        for (;;) {
            try {
                switch (selectStrategy.calculateStrategy(selectNowSupplier, hasTasks())) {
                    case SelectStrategy.CONTINUE:
                        continue;
                    case SelectStrategy.SELECT:
                        select(wakenUp.getAndSet(false));

                        // 'wakenUp.compareAndSet(false, true)' is always evaluated
                        // before calling 'selector.wakeup()' to reduce the wake-up
                        // overhead. (Selector.wakeup() is an expensive operation.)
                        //
                        // However, there is a race condition in this approach.
                        // The race condition is triggered when 'wakenUp' is set to
                        // true too early.
                        //
                        // 'wakenUp' is set to true too early if:
                        // 1) Selector is waken up between 'wakenUp.set(false)' and
                        //    'selector.select(...)'. (BAD)
                        // 2) Selector is waken up between 'selector.select(...)' and
                        //    'if (wakenUp.get()) { ... }'. (OK)
                        //
                        // In the first case, 'wakenUp' is set to true and the
                        // following 'selector.select(...)' will wake up immediately.
                        // Until 'wakenUp' is set to false again in the next round,
                        // 'wakenUp.compareAndSet(false, true)' will fail, and therefore
                        // any attempt to wake up the Selector will fail, too, causing
                        // the following 'selector.select(...)' call to block
                        // unnecessarily.
                        //
                        // To fix this problem, we wake up the selector again if wakenUp
                        // is true immediately after selector.select(...).
                        // It is inefficient in that it wakes up the selector for both
                        // the first case (BAD - wake-up required) and the second case
                        // (OK - no wake-up required).
                        
                        if (wakenUp.get()) {
                            selector.wakeup();
                        }
                        // fall through
                    default:
                }
        }
    }
       
    @Override
    public int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception {
        return hasTasks ? selectSupplier.get() : SelectStrategy.SELECT;
    }
```
首先selectStrategy.calculateStrategy主要是用来控制这次循环是执行跳过、select操作还是fallthrough。如果当前的NioEventLoop中还有未处理的
task，则执行selectSupplier.get方法，该方法会直接调用selector的selectNow操作这个非阻塞方法，Selector.selectNow()则检查自从上次select到
现在有没有可用的selection key，然后立即返回。执行完成之后会跳出switch执行下面的processSelectedKeys逻辑。

为了高效的利用CPU，NioEventLoop只要有未消费的task，则优先消费task。

如果没有task则需要进行一次select(wakenUp.getAndSet(false))操作，在这个方法的具体实现中会调用Selector的select方法，该方法是一个阻塞方法。
select操作主要是检查当前的selection key，哪些是available。因为select操作是阻塞操作，如果不想等待，可以使用Selector的wakeUp操作来进行
中断停止等待。但是如果当前没有select操作，那么下次执行的select或者selectNow操作会立即被唤醒。

wakeUp操作是一个开销比较大的操作，于是NioEventLoop中声明了wakenUp(AtomicBoolean)字段，用于控制selector.wakeup()的调用。调用wakeup之前
先wakenUp.compareAndSet(false, true)，如果set成功才执行Selector.wakeup()操作。

当用户提交新的任务时executor.execute(...)，会触发wakeup操作。

在上面的代码中有一堆注释，解释了上述代码为什么那样实现，说明了产生竞态条件的原因：在执行完wakenUp.getAndSet(false)之后，用户发起了
wakeup操作，然后执行select操作，这时select将立即返回。直到下次循环把wakeUp重新置为false，这期间所有的wakenUp.compareAndSet(false, true)
都会失败，因为现在wakeUp的值是true。所以接下来的select()都不能被wakeup。

下面看一下NioEventLoop的select操作：
```java
    private void select(boolean oldWakenUp) throws IOException {
        Selector selector = this.selector;
        try {
            int selectCnt = 0;
            long currentTimeNanos = System.nanoTime();
            long selectDeadLineNanos = currentTimeNanos + delayNanos(currentTimeNanos);
            for (;;) {
                long timeoutMillis = (selectDeadLineNanos - currentTimeNanos + 500000L) / 1000000L;
                if (timeoutMillis <= 0) {
                    if (selectCnt == 0) {
                        selector.selectNow();
                        selectCnt = 1;
                    }
                    break;
                }

                // If a task was submitted when wakenUp value was true, the task didn't get a chance to call
                // Selector#wakeup. So we need to check task queue again before executing select operation.
                // If we don't, the task might be pended until select operation was timed out.
                // It might be pended until idle timeout if IdleStateHandler existed in pipeline.
                if (hasTasks() && wakenUp.compareAndSet(false, true)) {
                    selector.selectNow();
                    selectCnt = 1;
                    break;
                }

                int selectedKeys = selector.select(timeoutMillis);
                selectCnt ++;

                if (selectedKeys != 0 || oldWakenUp || wakenUp.get() || hasTasks() || hasScheduledTasks()) {
                    // - Selected something,
                    // - waken up by user, or
                    // - the task queue has a pending task.
                    // - a scheduled task is ready for processing
                    break;
                }
                if (Thread.interrupted()) {
                    // Thread was interrupted so reset selected keys and break so we not run into a busy loop.
                    // As this is most likely a bug in the handler of the user or it's client library we will
                    // also log it.
                    //
                    // See https://github.com/netty/netty/issues/2426
                    if (logger.isDebugEnabled()) {
                        logger.debug("Selector.select() returned prematurely because " +
                                "Thread.currentThread().interrupt() was called. Use " +
                                "NioEventLoop.shutdownGracefully() to shutdown the NioEventLoop.");
                    }
                    selectCnt = 1;
                    break;
                }

                long time = System.nanoTime();
                if (time - TimeUnit.MILLISECONDS.toNanos(timeoutMillis) >= currentTimeNanos) {
                    // timeoutMillis elapsed without anything selected.
                    selectCnt = 1;
                } else if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 &&
                        selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD) {
                    // The selector returned prematurely many times in a row.
                    // Rebuild the selector to work around the problem.
                    logger.warn(
                            "Selector.select() returned prematurely {} times in a row; rebuilding Selector {}.",
                            selectCnt, selector);

                    rebuildSelector();
                    selector = this.selector;

                    // Select again to populate selectedKeys.
                    selector.selectNow();
                    selectCnt = 1;
                    break;
                }

                currentTimeNanos = time;
            }

            if (selectCnt > MIN_PREMATURE_SELECTOR_RETURNS) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Selector.select() returned prematurely {} times in a row for Selector {}.",
                            selectCnt - 1, selector);
                }
            }
        } catch (CancelledKeyException e) {
            if (logger.isDebugEnabled()) {
                logger.debug(CancelledKeyException.class.getSimpleName() + " raised by a Selector {} - JDK bug?",
                        selector, e);
            }
            // Harmless exception - log anyway
        }
    }
```
selectCnt标记select执行的次数，用于检测NIO的epoll bug。在这个方法尾部有一个判断：
```java
if (SELECTOR_AUTO_REBUILD_THRESHOLD > 0 &&
                        selectCnt >= SELECTOR_AUTO_REBUILD_THRESHOLD)
```
判断select执行的次数是否超过阀值，如果是的话有可能触发了NIO的epoll bug，执行重建selector的逻辑：新建一个Selector，把老的Selection Key
全部复制到新的Selector上，重建之后立即执行一次selectNow。

因为select操作是阻塞的，如果长时间没有IO可用，就会造成NioEventLoop的task积压。因此每一次select操作都必须要设置一个超时时间：
1. 查询定时任务最近要被执行的task还有多长时间执行，
2. 这个时间加上0.5s就是最大超时时间
```java
long selectDeadLineNanos = currentTimeNanos + delayNanos(currentTimeNanos);
long timeoutMillis = (selectDeadLineNanos - currentTimeNanos + 500000L) / 1000000L;
```
看一下select中的for循环：
1. 第一个if：如果timeoutMillis<=0，立即执行一次selectNow，然后退出循环消费task
2. 第二个if：如果当前的TaskQueue中还有任务，并且没有被wakeup，则执行一次selectNow，跳出循环消费task
3. 接下来执行select操作，并计次
4. 第三个if：判断是否有available keys或者被用户线程唤醒或者任务队列、定时队列中有任务则中断
5. 最后就是重建Selector

NioEventLoop.run方法的后半段逻辑主要是processSelectedKeys(处理IO)和runTasks(消费任务)。这里有一个参数用于控制处理这两种任务的时间
配比：ioRatio
```java
                final int ioRatio = this.ioRatio;
                if (ioRatio == 100) {
                    try {
                        processSelectedKeys();
                    } finally {
                        // Ensure we always run tasks.
                        runAllTasks();
                    }
                } else {
                    final long ioStartTime = System.nanoTime();
                    try {
                        processSelectedKeys();
                    } finally {
                        // Ensure we always run tasks.
                        final long ioTime = System.nanoTime() - ioStartTime;
                        runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                    }
                }
```
先来看一下processSelectedKeys，它的逻辑由processSelectedKeysOptimized和processSelectedKeysPlain实现，调用那个函数取决于你是否开启了
DISABLE_KEYSET_OPTIMIZATION。如果开启了Selection优化选项，则在创建Selector的时候以反射的方式把SelectedSelectionKeySet selectedKeys
设置到selector中。具体实现在openSelector中，代码就不贴出来了。SelectedSelectionKeySet内部是基于Array实现的，而Selector内部selectedKeys
是Set类型的，遍历效率Array效率更好一下。

下面看一下processSelectedKeysPlain的实现
```java
    private void processSelectedKeysPlain(Set<SelectionKey> selectedKeys) {
        if (selectedKeys.isEmpty()) {
            return;
        }

        Iterator<SelectionKey> i = selectedKeys.iterator();
        for (;;) {
            final SelectionKey k = i.next();
            final Object a = k.attachment();
            i.remove();

            if (a instanceof AbstractNioChannel) {
                processSelectedKey(k, (AbstractNioChannel) a);
            } else {
                @SuppressWarnings("unchecked")
                NioTask<SelectableChannel> task = (NioTask<SelectableChannel>) a;
                processSelectedKey(k, task);
            }

            if (!i.hasNext()) {
                break;
            }

            if (needsToSelectAgain) {
                selectAgain();
                selectedKeys = selector.selectedKeys();

                // Create the iterator again to avoid ConcurrentModificationException
                if (selectedKeys.isEmpty()) {
                    break;
                } else {
                    i = selectedKeys.iterator();
                }
            }
        }
    }
    private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
        final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
        if (!k.isValid()) {
            final EventLoop eventLoop;
            try {
                eventLoop = ch.eventLoop();
            } catch (Throwable ignored) {
                return;
            }
            if (eventLoop != this || eventLoop == null) {
                return;
            }
            unsafe.close(unsafe.voidPromise());
            return;
        }

        try {
            int readyOps = k.readyOps();
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                int ops = k.interestOps();
                ops &= ~SelectionKey.OP_CONNECT;
                k.interestOps(ops);

                unsafe.finishConnect();
            }

            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                ch.unsafe().forceFlush();
            }

            if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                unsafe.read();
            }
        } catch (CancelledKeyException ignored) {
            unsafe.close(unsafe.voidPromise());
        }
    }
```
SelectionKey上边可以挂载Attachment，一般情况下新的链接对象Channel会挂到attachment上。我们在遍历selectedKeys时，首先取出selection key
上的attachment，key的类型可能是AbstractNioChannel和NioTask。根据不同的类型调用不同的处理函数。我们着重看处理channel的逻辑：
1. 如果selection key是：SelectionKey.OP_CONNECT，那表明这是一个链接操作。对于链接操作，我们需要把这个selection key从interestOps中清除掉，
否则下次select操作会直接返回。接下来调用finishConnect方法。
2. 如果selection key是：SelectionKey.OP_WRITE。则执行flush操作，把数据刷到客户端。
3. 如果是read操作则调用unsafe.read()。

整体来看NioEventLoop的实现也不复杂，主要就干了两件事情：select IO以及消费task。因为select操作是阻塞的（尽管设置了超时时间），每次执行select时
都会检查是否有新的task，有则优先执行task。这么做也是做大限度的提高EventLoop的吞吐量，减少阻塞时间。除了这两件事儿，NioEventLoop还解决了JDK中注明
的EPoll bug。