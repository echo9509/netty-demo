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
