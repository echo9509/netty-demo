ChannelHandler类似于Servlet的Filter过滤器，负责对I/O事件或者I/O操作进行拦截和处理，他可以选择性地拦截和处理自己感兴趣的事件，也可以透传
和终止事件的传递。

基于ChannelHandler接口，用户可以方便地进行业务逻辑定制，例如打印日志、统一封装异常信息、性能统计和消息编解码等。

ChannelHandler支持注解，目前支持的注解有：
- Sharable：多个ChannelPipeline公用同一个ChannelHandler

# ChannelHandlerAdapter功能说明
ChannelHandlerAdapter是一个抽象类，基于它有两个子类，分别是：
- ChannelInboundHandlerAdapter：用于处理入站事件
- ChannelOutboundHandlerAdapter：用于处理出站时间

上述两个类默认实现了事件的透传，对于不关心的方法，我们可以直接继承父类的方法，如果关心某个事件，覆盖ChannelHandlerAdapter对应的方法即可。

# ByteToMessageDecoder功能
利用NIO进行网络编程时，往往需要将读取到的字节数组或者字节缓冲区解码为业务可以使用POJO对象，为了方便业务将ByteBuf解码成业务POJO对象，Netty
提供了ByteToMessageDecoder抽象工具解码类。

用户的解码器继承ByteToMessageDecoder，只需要实现decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)抽象方法即可完成
ByteBuf到POJO对象的解码。

由于ByteToMessageDecoder并没有考虑TCP粘包和组包等场景，读半包需要用户解码器自己负责处理。因此正式场合不会直接继承ByteToMessageDecoder，
而是继承另外一些更高级的解码器来屏蔽半包的处理。

# MessageToMessageDecoder功能
MessageToMessageDecoder实际上是Netty的**二次解码器**，它的职责是将一个对象解码为其他对象。

从SocketChannel读取到的TCP数据是ByteBuffer，实际就是字节数组，首先需要将ByteBuffer缓冲区中的数据报读取出来，并将其解码为Java对象；然后
对Java对象根据某些规则做二次解码，将其解码为另一个POJO对象。MessageToMessageDecoder在ByteToMessageDecoder之后，所以称其为二次解码器。

用户的解码器只需要实现decode(ChannelHandlerContext ctx, I msg, List<Object> out)抽象方法即可，由于它是将一个POJO解码为另一个POJO，
所以一般不会涉及到半包的处理。

# LengthFieldBasedFrameDecoder功能
Netty提供的半包解码器有三种：
- LineBasedFrameDecoder：以换行符区分一个整包进行解码，处理半包问题
- DelimiterBasedFrameDecoder：以固定的分隔符为区分一个整包进行解码，处理半包问题
- LengthFieldBasedFrameDecoder：基于长度区分一个整包解码，处理半包问题，这是最通用的半包解码器

区分整包消息的4个常用方法：
- 固定长度：例如每120个字节代表一个整包消息，不足的前面补0。解码器在处理这类消息时只需要每次读到指定长度的字节后再进行解码
- 通过回车换行符区分消息，例如FTP协议。这类区分消息的方式多用于文本协议。
- 通过分隔符区分整包消息
- 通过指定长度来标识整包消息

下面看一下通过参数的不同组合来实现不同的半包读取策略。第一种常用的方式是消息的第一个字段是长度字段，后面是消息体，消息头中只包含一个长度字段，
定义如下：
![Fmg5aq.png](https://s1.ax1x.com/2018/11/30/Fmg5aq.png)
使用以下参数组合进行解码：
- lengthFieldOffset = 0
- lengthFieldLength = 2
- lengthAdjustment = 0
- initialBytesToStrip = 0

解码后的缓冲区内容：
![FmgbzF.png](https://s1.ax1x.com/2018/11/30/FmgbzF.png)
上述解码后的消息带有消息的长度字段，如果需要抛弃长度字段则需要使用以下配置，长度字段在起始位置且长度为2，所以将initialBytesToStrip设置为2：
- lengthFieldOffset = 0
- lengthFieldLength = 2
- lengthAdjustment = 0
- initialBytesToStrip = 2

解码后的缓冲区内容：
![FmWpvD.png](https://s1.ax1x.com/2018/11/30/FmWpvD.png)

在大多数场景中，长度仅用来标识消息体的长度，这类协议通常由消息长度字段和消息体组成。但是对于一些协议，长度还包含了消息头的长度。在这种场景下，
需要使用lengthAdjustment进行修正。由于整个消息的长度往往都大于消息体的长度，所以，lengthAdjustment为负数，修正后的参数组合如下：
- lengthFieldOffset = 0
- lengthFieldLength = 2
- lengthAdjustment = -2
- initialBytesToStrip = 0

![FmhGcT.png](https://s1.ax1x.com/2018/11/30/FmhGcT.png)

由于协议种类繁多，当标识消息长度的字段位于消息头的中间或尾部时，需要使用lengthFieldOffset字段来进行标识
![Fm4Daj.png](https://s1.ax1x.com/2018/11/30/Fm4Daj.png)
这里的长度字段占3个字节，Header1占2个字节，因此组合如下：
- lengthFieldOffset = 2
- lengthFieldLength = 3
- lengthAdjustment = 0
- initialBytesToStrip = 0

最后一种场景是长度字段夹在两个消息头之间或者长度字段位于消息头的中间，前后都其他的消息头字段，在这种场景下如果想忽略长度字段以及前面的其他消息
头字段，则可以通过initialBytesToStrip参数来跳过要忽略的字节长度
![Fm5onS.png](https://s1.ax1x.com/2018/11/30/Fm5onS.png)
由于HDR1长度为1个字节，因此lengthFieldOffset为1；长度字段为两个字节，因此lengthFieldLength为2；由于长度字段是消息体的长度，解码后如果
想要携带消息头中的字段，需要使用lengthAdjustment进行调整，这里的值为1，表示HDR2的长度；由于要忽略HDR1和长度字段，因此这里的initialBytesToStrip
为3，组合如下：
- lengthFieldOffset = 1
- lengthFieldLength = 2
- lengthAdjustment = 1
- initialBytesToStrip = 3

# MessageToByteEncoder功能
这是一个抽象类，负责将Java的POJO对象编码成ByteBuf，用户的编码器只需要继承MessageToByteEncoder，实现它的encode(ChannelHandlerContext ctx, I msg, ByteBuf out)
方法即可。

# MessageToMessageEncoder功能
该类将一个POJO对象转换成另一个对象，以HTTP+XML协议为例，它的一种实现方式是：将POJO对象编码成XML字符串，再将字符串编码成HTTP请求或者应答消息。

用户的编码器只需要继承MessageToMessageEncoder编码器，实现encode(ChannelHandlerContext ctx, I msg, List<Object> out)方法即可。它与
MessageToByteEncoder的区别是输出对象是对象列表而不是ByteBuf。

# LengthFieldPrepender功能
如果协议中的第一个字段为长度字段，Netty中提供了LengthFieldPrepender编码器，它可以计算当前待发送消息的二进制字节长度，并把该长度添加到ByteBuf的
缓冲区头中,如下图所示：
![FmIWE4.png](https://s1.ax1x.com/2018/11/30/FmIWE4.png)

通过LengthFieldPrepender可以将待发送消息的长度写入到ByteBuf的前两个字节，编码后的消息组成为长度字段+原消息的方式。

通过设置LengthFieldPrepender中的lengthIncludesLengthFieldLength属性为true，消息长度将包含长度本身占用的字节数。

# ChannelHandler源码分析
## ChannelHandler类关系
相对于ByteBuf和Channel，ChannelHandler的类继承关系相对简单，但是它的子类非常多。由于ChannelHandler是Netty框架和用户代码的主要扩展和
定制点，所以它的子类种类繁多、功能各异，系统ChannelHandler主要分类如下：
- ChannelPipeline的系统ChannelHandler，用于I/O操作和对事件进行预处理，对于用户不可见，这类ChannelHandler主要包括HeadContext和TailContext
- 编解码ChannelHandler，包括ByteToMessageCodec、MessageToMessageDecoder等，这些编解码类本身又包含多种子类。
- 其他功能性ChannelHandler，包括流量整形Handler、读写超时Handler、日志Handler等

下面的图片设计我们前面提到过得所有编解码器的继承关系
![FmTllt.png](https://s1.ax1x.com/2018/11/30/FmTllt.png)

## ByteToMessageDecoder源码分析
ByteToMessageDecoder解码器用于将ByteBuf解码成POJO对象。
```java
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            CodecOutputList out = CodecOutputList.newInstance();
            try {
                ByteBuf data = (ByteBuf) msg;
                first = cumulation == null;
                if (first) {
                    cumulation = data;
                } else {
                    cumulation = cumulator.cumulate(ctx.alloc(), cumulation, data);
                }
                callDecode(ctx, cumulation, out);
            } catch (DecoderException e) {
                throw e;
            } catch (Exception e) {
                throw new DecoderException(e);
            } finally {
                if (cumulation != null && !cumulation.isReadable()) {
                    numReads = 0;
                    cumulation.release();
                    cumulation = null;
                } else if (++ numReads >= discardAfterReads) {
                    // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    numReads = 0;
                    discardSomeReadBytes();
                }

                int size = out.size();
                decodeWasNull = !out.insertSinceRecycled();
                fireChannelRead(ctx, out, size);
                out.recycle();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }
```
首先判断需要解码的是否是ByteBuf，如果是ByteBuf才需要解码，否则直接透传。

接着通过cumulation是否为空判断解码器是否缓存了没有解码完成的半包消息，如果为空，说明是首次解码或者最近一次处理完了半包消息，没有缓存的半包消息
需要处理，直接将需要解码的ByteBuf赋值给cumulation；如果cumulation缓存上有上次没有解码完成的ByteBuf，则进行复制操作，将需要解码的ByteBuf
复制到cumulation中。

复制操作完成之后释放需要解码的ByteBuf对象，调用callDecode方法进行解码。对ByteBuf进行循环解码，循环的条件是解码缓冲区对象中有可读的字节，
调用decode方法，由用户的子类解码器进行解码。

解码后需要对当前的pipeline状态和解码结果进行判断。如果当前ChannelHandlerContext已经被移除，则不能继续进行解码，直接退出循环；如果输出的
out列表长度没有变化，说明没有解码没有成功，需要针对以下不同场景进行判断：
1. 如果用户解码器没有消费ByteBuf，则说明是个半包消息，需要由I/O线程继续读取后续的数据报，在这种场景下腰退出循环
2. 如果用户解码器消费了ByteBuf，说明解码可以继续进行。

如果用户解码器没有消费ByteBuf，但是却解码出一个或者多个对象，这种行为被认为是非法的，需要抛出DecoderException异常。

最后通过isSingleDecode进行判断，如果是单条消息解码器，第一次解码完成之后就退出循环。

```java
    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            while (in.isReadable()) {
                int outSize = out.size();

                if (outSize > 0) {
                    fireChannelRead(ctx, out, outSize);
                    out.clear();

                    // Check if this handler was removed before continuing with decoding.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/4635
                    if (ctx.isRemoved()) {
                        break;
                    }
                    outSize = 0;
                }

                int oldInputLength = in.readableBytes();
                decodeRemovalReentryProtection(ctx, in, out);

                // Check if this handler was removed before continuing the loop.
                // If it was removed, it is not safe to continue to operate on the buffer.
                //
                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }

                if (outSize == out.size()) {
                    if (oldInputLength == in.readableBytes()) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                                    ".decode() did not read anything but decoded a message.");
                }

                if (isSingleDecode()) {
                    break;
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception cause) {
            throw new DecoderException(cause);
        }
    }
```
## MessageToMessageDecoder源码
MessageToMessageDecoder负责将一个POJO对象解码成另一个POJO对象。
```java
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                try {
                    decode(ctx, cast, out);
                } finally {
                    ReferenceCountUtil.release(cast);
                }
            } else {
                out.add(msg);
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            int size = out.size();
            for (int i = 0; i < size; i ++) {
                ctx.fireChannelRead(out.getUnsafe(i));
            }
            out.recycle();
        }
    }
```
首先从本地ThreadLocal中获取一个CodecOutputList，然后判断该消息是不是已经被解码对象，如果已经被解码过，则直接添加到CodecOutputList中，
如果没有，则需要解码消息并将其添加到CodecOutputList。最后对CodecOutputList进行便利，调用ChannelHandlerContext的fireChannelRead方法，
通知后续的ChannelHandler继续进行处理。循环通知完成以后，需要将CodecOutputList进行释放。

## LengthFieldBasedFrameDecoder源码
这是基于长度的半包解码器。
```java
    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }
```
入口方法会调用内部的decode(ChannelHandlerContext ctx, ByteBuf in)方法，如果解码成功，将其加入到输出的List<Object> out列表中。
```java
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (discardingTooLongFrame) {
            discardingTooLongFrame(in);
        }
    }
```
首先判断discardingTooLongFrame标识，看是否需要丢弃当前可读的字节缓冲区，如果为真，则执行丢弃操作。
```java
    private void discardingTooLongFrame(ByteBuf in) {
        long bytesToDiscard = this.bytesToDiscard;
        int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
        in.skipBytes(localBytesToDiscard);
        bytesToDiscard -= localBytesToDiscard;
        this.bytesToDiscard = bytesToDiscard;

        failIfNecessary(false);
    }
```
判断需要丢弃的字节长度，由于丢弃的字节数不能大于当前缓冲区可读的字节数，所以需要通过Math.min函数进行选择，取bytesToDiscard和缓冲区可读
字节数之中的最小值。计算获取需要丢弃的字节数之后，调用ByteBuf的skipBytes方法跳过需要忽略的字节长度，然后bytesToDiscard减去已经忽略的字
节长度。最后判断是否已经达到需要忽略的字节数，达到的话对discardingTooLongFrame等进行置位，代码如下：
```java
    private void failIfNecessary(boolean firstDetectionOfTooLongFrame) {
        if (bytesToDiscard == 0) {
            // Reset to the initial state and tell the handlers that
            // the frame was too large.
            long tooLongFrameLength = this.tooLongFrameLength;
            this.tooLongFrameLength = 0;
            discardingTooLongFrame = false;
            if (!failFast || firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        } else {
            // Keep discarding and notify handlers if necessary.
            if (failFast && firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        }
    }
```
在进行字节丢弃操作之后，紧接着对当前缓冲区的可读字节数和长度偏移量进行对比，如果小于长度偏移量，则说明当前缓冲区的数据包不够，需要返回空，
由I/O线程继续读取后续的数据包，代码如下所示：
```java
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
   
        if (in.readableBytes() < lengthFieldEndOffset) {
            return null;
        }
    }
```
接着通过读索引和lengthFieldOffset计算获取实际的长度索引，然后通过索引值获取消息报文的长度字段。
```java
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {

        int actualLengthFieldOffset = in.readerIndex() + lengthFieldOffset;
        long frameLength = getUnadjustedFrameLength(in, actualLengthFieldOffset, lengthFieldLength, byteOrder);
    }
```
根据长度字段自身的字节长度进行判断，共有以下6中可能的取值：
- 长度所占字节为1：通过ByteBuf的getUnsignedByte方法获取长度值
- 长度所占字节为2：通过ByteBuf的getUnsignedShort方法获取长度值
- 长度所占字节为3：通过ByteBuf的getUnsignedMedium方法获取长度值
- 长度所占字节为4：通过ByteBuf的getUnsignedInt方法获取长度值
- 长度所占字节为8：通过ByteBuf的getLong方法获取长度值
- 其他长度不支持，抛出DecoderException异常
```java
    protected long getUnadjustedFrameLength(ByteBuf buf, int offset, int length, ByteOrder order) {
        buf = buf.order(order);
        long frameLength;
        switch (length) {
        case 1:
            frameLength = buf.getUnsignedByte(offset);
            break;
        case 2:
            frameLength = buf.getUnsignedShort(offset);
            break;
        case 3:
            frameLength = buf.getUnsignedMedium(offset);
            break;
        case 4:
            frameLength = buf.getUnsignedInt(offset);
            break;
        case 8:
            frameLength = buf.getLong(offset);
            break;
        default:
            throw new DecoderException(
                    "unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
        }
        return frameLength;
    }
```
获取长度之后，需要对长度进行合法性判断，同时根据其他解码参数进行长度调整。

如果长度小于0，说明报文非法，跳过lengthFieldEndOffset字节，抛出CorruptedFrameException异常。

根据lengthAdjustment和lengthFieldEndOffset字段进行长度修正，如果修正后的报文长度小于lengthFieldEndOffset，则说明是非法数据，
需要抛出CorruptedFrameException异常。

如果修正后的报文长度大于ByteBuf的最大容量，说明接收到的消息长度大于系统允许的最大长度上线，需要设置discardingTooLongFrame，计算需要
丢弃的字节数，根据情况选择是否需要抛出解码异常。
代码如下：
```java
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
    
        if (frameLength < 0) {
            failOnNegativeLengthField(in, frameLength, lengthFieldEndOffset);
        }

        frameLength += lengthAdjustment + lengthFieldEndOffset;

        if (frameLength < lengthFieldEndOffset) {
            failOnFrameLengthLessThanLengthFieldEndOffset(in, frameLength, lengthFieldEndOffset);
        }

        if (frameLength > maxFrameLength) {
            exceededFrameLength(in, frameLength);
            return null;
        }
    }
```
丢弃的策略如下：frameLength减去ByteBuf的可读字节数就是要丢弃的字节长度，如果需要丢弃的字节数discard小于缓冲区可读的字节数，则直接丢弃整包
消息。如果需要丢弃的字节数大于当前的可读字节数，说明即便将当前所有可读的字节数全部丢弃，也无法完成任务，则设置discardingTooLongFrame为true，
下次解码的时候继续丢弃。丢弃操作完成之后，调用failIfNecessary方法根据实际情况抛出异常。
```java
    private void exceededFrameLength(ByteBuf in, long frameLength) {
        long discard = frameLength - in.readableBytes();
        tooLongFrameLength = frameLength;

        if (discard < 0) {
            // buffer contains more bytes then the frameLength so we can discard all now
            in.skipBytes((int) frameLength);
        } else {
            // Enter the discard mode and discard everything received so far.
            discardingTooLongFrame = true;
            bytesToDiscard = discard;
            in.skipBytes(in.readableBytes());
        }
        failIfNecessary(true);
    }
```
如果当前可读的字节数小于frameLength，说明是个半包消息，需要返回空，由I/O线程继续读取后续的数据包，等待下次解码。

对需要忽略的消息头字段进行判断，如果长度大于消息长度frameLength，说明码流非法，需要忽略当前的数据包，抛出CorruptedFrameException异常。

通过ByteBuf的skipBytes方法忽略消息头中不需要的字段，得到整包ByteBuf。

通过extractFrame方法换区解码后的整包缓冲区消息。
```java
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {

        // never overflows because it's less than maxFrameLength
        int frameLengthInt = (int) frameLength;
        if (in.readableBytes() < frameLengthInt) {
            return null;
        }

        if (initialBytesToStrip > frameLengthInt) {
            failOnFrameLengthLessThanInitialBytesToStrip(in, frameLength, initialBytesToStrip);
        }
        in.skipBytes(initialBytesToStrip);

        // extract frame
        int readerIndex = in.readerIndex();
        int actualFrameLength = frameLengthInt - initialBytesToStrip;
        ByteBuf frame = extractFrame(ctx, in, readerIndex, actualFrameLength);
        in.readerIndex(readerIndex + actualFrameLength);
        return frame;
    }
```
extractFrame方法的具体执行逻辑是根据消息的实际长度分配一个新的ByteBuf对象，将需要解码的ByteBuf可读缓冲区复制到新创建的ByteBuf中并返回，
返回之后更新原解码缓冲区ByteBuf为原读索引+消息报文的实际长度。

## MessageToByteEncoder源码
MessageToByteEncoder负责将用户的POJO对象编码成ByteBuf。ßß
```java
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = null;
        try {
            if (acceptOutboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                buf = allocateBuffer(ctx, cast, preferDirect);
                try {
                    encode(ctx, cast, buf);
                } finally {
                    ReferenceCountUtil.release(cast);
                }

                if (buf.isReadable()) {
                    ctx.write(buf, promise);
                } else {
                    buf.release();
                    ctx.write(Unpooled.EMPTY_BUFFER, promise);
                }
                buf = null;
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }
```
首先判断当前编码器是否支持需要发送的消息，如果不支持则直接透传；如果支持则判断缓冲区的类型，对于直接内存通过ioBuffer方法分配，对于堆内存
通过heapBuffer分配(上述逻辑在allocateBuffer方法中)。

编码使用的缓冲区分配完成之后，调用encode抽象方法进行编码。

编码完成后，调用ReferenceCountUtil的release方法释放编码对象msg。对编码后的ByteBuf进行以下判断：
- 如果缓冲区包含发送的字节，则调用ChannelHandlerContext的write方法发送ByteBuf
- 如果缓冲区没有包含可发送的字节，则需要释放编码后的ByteBuf，写入一个空的ByteBuf到ChannelHandlerContext中。

发送完成后，释放编码缓冲区的ByteBuf对象。

## MessageToMessageEncoder源码
MessageToMessageEncoder负责将一个POJO对象编码成另一个POJO对象。
```java
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        CodecOutputList out = null;
        try {
            if (acceptOutboundMessage(msg)) {
                out = CodecOutputList.newInstance();
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                try {
                    encode(ctx, cast, out);
                } finally {
                    ReferenceCountUtil.release(cast);
                }

                if (out.isEmpty()) {
                    out.recycle();
                    out = null;

                    throw new EncoderException(
                            StringUtil.simpleClassName(this) + " must produce at least one message.");
                }
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            if (out != null) {
                final int sizeMinusOne = out.size() - 1;
                if (sizeMinusOne == 0) {
                    ctx.write(out.get(0), promise);
                } else if (sizeMinusOne > 0) {
                    // Check if we can use a voidPromise for our extra writes to reduce GC-Pressure
                    // See https://github.com/netty/netty/issues/2525
                    ChannelPromise voidPromise = ctx.voidPromise();
                    boolean isVoidPromise = promise == voidPromise;
                    for (int i = 0; i < sizeMinusOne; i ++) {
                        ChannelPromise p;
                        if (isVoidPromise) {
                            p = voidPromise;
                        } else {
                            p = ctx.newPromise();
                        }
                        ctx.write(out.getUnsafe(i), p);
                    }
                    ctx.write(out.getUnsafe(sizeMinusOne), promise);
                }
                out.recycle();
            }
        }
    }
```
与之前的编码器类似，创建CodecOutputList对象，判断当前需要编码的对象是否是编码器可处理的类型，如果不是，则透传。

如果是则由具体的编码子类负责完成，如果编码后的CodecOutputList为空，则说明编码失败，释放CodecOutputList的引用。

如果编码成功，则遍历CodecOutputList，循环发送编码后的POJO对象。

## LengthFieldPrepender源码
LengthFieldPrepender负责在待发送的ByteBuf消息头中增加一个长度字段来标识消息的长度，它简化了用户的编码器的开发，使用户不需要额外去设置
这个长度字段。

首先对长短字段进行设置，如果需要包含消息长度自身，则在原来的长度的基础上再加上lengthFieldLength的长度。

如果调整后的消息长度小于0，则抛出IllegalArgumentException异常。对消息长度自身所占的字节数进行判断，以便采用正确的方法将长度字段写入到
ByteBuf中，一共有6中可能：
- 长度字段所占字节为1：如果使用1个Byte字节代表消息长度，则最大长度需要小于256个字节。对长度校验，失败抛出异常，成功则创建新的ByteBuf并通过
writeByte将长度值写入到ByteBuf中
- 长度字段所占字节为2：如果使用2个Byte字节代表消息长度，则最大长度需要小于65536个字节。对长度校验，失败抛出异常，成功则创建新的ByteBuf并通过
writeShort将长度值写入到ByteBuf中
- 长度字段所占字节为3：如果使用3个Byte字节代表消息长度，则最大长度需要小于16777216个字节。对长度校验，失败抛出异常，成功则创建新的ByteBuf并通过
writeMedium将长度值写入到ByteBuf中
- 长度字段所占字节为4：创建新的ByteBuf，并通过writeInt方法将长度值写入到ByteBuf中
- 长度字段所占字节为8：创建新的ByteBuf，并通过writeLonge方法将长度值写入到ByteBuf中。
- 其他长度值：直接抛出Error

最后将原需要发送的ByteBuf复制到List<Object> out中，完成编码。
```java
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        int length = msg.readableBytes() + lengthAdjustment;
        if (lengthIncludesLengthFieldLength) {
            length += lengthFieldLength;
        }

        if (length < 0) {
            throw new IllegalArgumentException(
                    "Adjusted frame length (" + length + ") is less than zero");
        }

        switch (lengthFieldLength) {
        case 1:
            if (length >= 256) {
                throw new IllegalArgumentException(
                        "length does not fit into a byte: " + length);
            }
            out.add(ctx.alloc().buffer(1).order(byteOrder).writeByte((byte) length));
            break;
        case 2:
            if (length >= 65536) {
                throw new IllegalArgumentException(
                        "length does not fit into a short integer: " + length);
            }
            out.add(ctx.alloc().buffer(2).order(byteOrder).writeShort((short) length));
            break;
        case 3:
            if (length >= 16777216) {
                throw new IllegalArgumentException(
                        "length does not fit into a medium integer: " + length);
            }
            out.add(ctx.alloc().buffer(3).order(byteOrder).writeMedium(length));
            break;
        case 4:
            out.add(ctx.alloc().buffer(4).order(byteOrder).writeInt(length));
            break;
        case 8:
            out.add(ctx.alloc().buffer(8).order(byteOrder).writeLong(length));
            break;
        default:
            throw new Error("should not reach here");
        }
        out.add(msg.retain());
    }
```