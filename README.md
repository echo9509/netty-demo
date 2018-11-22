# ByteBuf
ByteBuf需要提供JDK ByteBuffer的功能(包含且不限于)，主要有以下几类基本功能：
1. 7种Java基础类型、byte[]、ByteBuffer(ByteBuf)的等的读写
2. 缓冲区自身的copy和slice
3. 设置网络字节序
4. 构造缓冲区实例
5. 操作位置指针

## 扩容原理
1. 首先确认ByteBuf是否已经被释放，如果被释放，则抛出IllegalReferenceCountException异常
2. 判断写入需要的最小空间，如果该空间小于ByteBuf的可写入空间，直接返回，不进行扩容
3. 判断写入需要的最小空间，如果该空间大于ByteBuf的(最大容量-当前的写索引)，不进行扩容，抛出IndexOutOfBoundsException异常
4. 计算新容量，动态扩容的规则，当新容量大于4MB时，以4MB的方式递增扩容，在小于4MB时，从64字节开始倍增(Double)扩容

## 读写索引
Netty提供readIndex和writeIndex用来支持读取和写入操作，两个索引将缓冲区划分为三个区域
1. 0 ~ readIndex：已读区域(可丢弃区域)
2. readIndex ~ writeIndex：未读取区域
3. writeIndex ~ capacity：待写入区域

## 已读区域(Discardable Bytes)
位于已读区域的内容表明该内容已被Netty处理完成，我们可以重用这块缓冲区，尽量减少缓冲区的动态扩容(复制，耗时操作)。

调用discardBytes()方法可以清除已读区域内容，但同时会导致未读区域的左移，也是将未读区域的内容复制到原来的已读区域(耗时)，
因此频繁的调用discardBytes也是不可取的，可以根据实际情况进行调用。

## Readable Bytes和Writable Bytes
Readable Bytes(可读空间)存储的是未被读取处理的内容，以read或者skip开头的方法都会从readIndex开始读取或者跳过指定的数据，同时readIndex会增加读取或跳过
的字节数长度。如果读取的字节数长度大于实际可读取的字节数，抛出IndexOutOfBoundsException异常。

Writable Bytes(可写入空间)是未被数据填充的缓冲区块，以write开头的操作都会从writeIndex开始向缓冲区写入数据，同时writeIndex会增加写入的数据的字节数长度。
如果写入的字节数大于可写入的字节数，会抛出IndexOutOfBoundsException异常。

## Clear
Clear操作并不会清除缓冲区的内容，只是将readIndex和writeIndex还原为初始分配值。

## Mark和Reset
1. markReadIndex
2. resetReadIndex
3. markWriteIndex
4. resetWriteIndex

## 查找操作
1. indexOf(int fromIndex, int toIndex, byte value)：fromIndex<=toIndex时，从头开始查找首次出现value的位置(查找范围fromIndex ~ toIndex)，
当fromIndex > toIndex时，倒着查找首次出现value的位置（查找的范围toIndex ~ fromIndex - 1），查不到返回-1
2. bytesBefore(byte value)：从ByteBuf的可读区域中首次定位出现value的位置，没有找到返回-1。该方法不会修改readIndex和writeIndex
3. bytesBefore(int length, byte value)：从ByteBuf的可读区域中定位首次出现value的位置，结束索引是readIndex+length。如果length大于可读字节数，
抛出IndexOutOfBoundsException异常
4.bytesBefore(int index, int length, byte value)：从ByteBuf中定位首次出现value的位置，起始索引为index，结束索引为index+length，如果index+length
大于当前缓冲区的容量，抛出IndexOutOfBoundsException异常
5. forEachByte(int index, int length, ByteProcessor processor)：从index开始，到index + length结束，与ByteProcessor设置的查找条件进行对比，
满足条件，返回位置索引，否则返回-1
6. forEachByteDesc(ByteProcessor processor)：倒序遍历ByteBuf的可读字节数组，与ByteProcessor设置的查找条件进行对比，满足条件，返回位置索引，否则返回-1
7. forEachByteDesc(int index, int length, ByteProcessor processor)：以index + length - 1开始，直到index结束，倒序遍历ByteBuf字节数组，
与ByteProcessor设置的查找条件进行对比，满足条件，返回位置索引，否则返回-1

Netty提供了大量的默认的ByteProcessor，来对常用的查找自己进行查找，具体可见ByteProcessor接口。

## Derived buffers(派生缓冲区)
1. duplicate()：返回当前ByteBuf的复制对象，复制后返回的ByteBuf与操作的ByteBuf共享缓冲区内容，但是维护自己独立的读写索引。当修改复制后的ByteBuf内容后，
原ByteBuf的内容也随之改变，因为双方持有的是同一个内容的指针引用。
2. copy()：复制一个新的ByteBuf对象，内容和索引都与原ByteBuf独立，复制操作本身并不修改原ByteBuf的读写索引
3. copy(int index, int length)：复制一个新的ByteBuf对象，复制开始的索引为index，复制的长度为length
3. slice()：返回与当前ByteBuf的可读子缓冲区，范围是readIndex ~ writeIndex，返回后的ByteBuf与原ByteBuf内容共享，读写索引独立维护，
maxCapacity是当前ByteBuf的可读字节数(换句话说就是这个新返回的缓冲区不能再进行写入)
4. slice(int index, int length)：返回index开始，length长度的当前ByteBuf的子缓冲区，返回后的ByteBuf与原ByteBuf内容共享，读写索引独立维护，
maxCapacity是length(换句话说就是这个新返回的缓冲区不能再进行写入)

## 转换成标准的ByteBuffer
1. ByteBuffer nioBuffer()：将当前ByteBuf可读的缓冲区转换成ByteBuffer，两者共享同一个缓冲区内容引用，对ByteBuffer的读写操作并不会修改原ByteBuf的读写索引。
返回后的ByteBuffer无法感知ByteBuf的动态扩展。
2. ByteBuffer nioBuffer(int index, int length)：从ByteBuf的index位置开始长度为length的缓冲区转换成ByteBuffer，两者共享同一个缓冲区内容引用，
对ByteBuffer的读写操作并不会修改原ByteBuf的读写索引。返回后的ByteBuffer无法感知ByteBuf的动态扩展。

## 随机读写
主要通过set和get开头的方法，这两个方法可以指定索引位置。

# ByteBuf源码
从内存分配的角度来看，ByteBuf主要分为以下两类：
1. 堆内存(HeapByteBuf)字节缓冲区：内存分配和回收速度快，可以被JVM自动回收；缺点是如果Socket进行I/O读写，需要进行一次内存复制，将堆内存对应的缓冲区复制到内核
Channel中，性能会有所下降
2. 直接内存(DirectByteBuf)字节缓冲区：堆外内存直接分配，相比于堆内存，分配和回收速度比较慢，但是在Socket Channel中进行读写比较快(少一次内存复制)

ByteBuf的最佳时间是在I/O通信线程的读写缓冲区使用DirectByteBuf，后端业务消息的编解码模块使用HeapByteBuf。

从内存回收的角度进行分类：
1. 基于对象池的ByteBuf：自己维护了一个内存池，可以重复利用ByteBuf对象，提升内存使用率，降低GC频率
2. 普通的ByteBuf

## AbstractByteBuf
AbstractByteBuf继承ByteBuf，ByteBuf中的一些公共属性和方法会在AbstractByteBuf中实现。
### 主要变量
1. ResourceLeakDetector<ByteBuf> leakDetector对象：被定义为static，所有的ByteBuf实例共享一个ResourceLeakDetector<ByteBuf> leakDetector对象。
ResourceLeakDetector主要用来检测对象是否泄漏。
2. 索引设置：读写索引、重置读写索引、最大容量

### 读操作
读操作的公共功能由父类实现，差异化由具体的子类实现。

选取readBytes(byte[] dst, int dstIndex, int length)分析：
1. 首先对缓冲区可读空间进行校验：如果读取的长度(length) < 0，会抛出IllegalArgumentException异常；如果可读的字节数小于需要读取的长度(length)，
会抛出IndexOutOfBoundsException异常
2. 校验通过之后，调用getBytes方法从当前的读索引开始进行读取（这一块就需要由真正的子类来各自实现），复制length个字节到目标byte数组，数组开始的位置是dstIndex
3. 读取成功后，对读索引进行递增，增加的长度为length

### 写操作
写操作的公共功能由父类实现，差异化由具体的子类实现。

选取writeBytes(byte[] src, int srcIndex, int length)分析：
1. 首先对缓冲区的可写空间进行校验：如果要写入的长度(length) < 0，会抛出IllegalArgumentException异常；如果要写入的长度小于缓冲区可写入的字节数，表明可写；
如果要写入的长度 > 最大容量 - writeIndex，会抛出IndexOutOfBoundsException；否则进行扩容操作（扩容操作的原理前面已经讲过）。

### 操作索引
与索引相关的操作主要涉及设置读写索引、mark、和reset等。

选取readerIndex(int readerIndex)进行分析：
1. 首先对索引合法性进行判断：如果readerIndex小于0或者readerIndex > writeIndex，则抛出IndexOutOfBoundsException异常
2. 校验通过之后，将读索引设置为readerIndex

### 重用缓冲区
选取discardReadBytes()进行分析：
1. 如果readIndex等于0，直接返回
2. 如果readIndex和writeIndex不相等，首先调用setBytes(int index, ByteBuf src, int srcIndex, int length)方法进行字节数组的复制，
然后重新设置markReadIndex、markWriteIndex、readIndex和writeIndex
3. 如果readIndex等于writeIndex，调整markReadIndex和markWriteIndex，不进行字节数组复制，设置readIndex=writeIndex=0

### skipBytes
1. 校验跳过的字节长度：如果跳过的字节长度小于0，则抛出IllegalArgumentException异常，如果跳过的字节数大于可读取的字节数，
则抛出IndexOutOfBoundsException异常
2. 校验通过之后，readIndex增加跳过的字节长度