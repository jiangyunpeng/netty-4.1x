## 改动点

1、新增 ByteBufAllocator metrics

http://172.16.49.101:8864/netty/allocator
![](doc/metrics.png)

2、新增 NettyAllocatorTest

3、增加 PoolArena debug日志

4、修改了 AbstractNioByteChannel，写入MDC，区分server还是client
