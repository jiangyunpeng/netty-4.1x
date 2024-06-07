package mytest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.SourceLogger;
import org.junit.Test;

/**
 * https://zhuanlan.zhihu.com/p/349119263
 * - small区域1~38 16b~28k
 * - normal区域38~75,32k~16m
 * - pageSize=8192
 */
public class ByteBufAllocatorTest {

    private static final int page_size = 8192;

    private int tiny = 1024;
    private int small = 1024;
    private int normal = 32 * 1024; //32k
    private int big = 1 * 1024 * 1024; //1mb

    static{
        System.setProperty("io.netty.allocator.useCacheForAllThreads", "false");
    }

    private int bytes(int kb){
        return 1024*kb;
    }

    @Test
    public void testPrintPage() {
        for (int i = 1; i < 10; ++i) {
            SourceLogger.info(this.getClass(), "page %s => %s", i, i * page_size);
        }
    }

    @Test
    public void test3kb() {
        ByteBufAllocator byteBufAllocator = new PooledByteBufAllocator(false);//PooledByteBufAllocator.DEFAULT;
        int[] items = {3000,100,500,700};
        for (int i = 0; i < items.length; ++i) {
            int reqSize = items[i];
            SourceLogger.info(this.getClass(), "===========" + i + "===========");
            ByteBuf byteBuf = byteBufAllocator.buffer(reqSize);//3072
            byteBuf.writeBytes("hello".getBytes());
        }
    }

    @Test
    public void test12k() {
        ByteBufAllocator byteBufAllocator = new PooledByteBufAllocator(false);//PooledByteBufAllocator.DEFAULT;
        for (int i = 0; i < 4; ++i) {
            SourceLogger.info(this.getClass(), "===========" + i + "===========");
            ByteBuf byteBuf = byteBufAllocator.buffer(bytes(12));
            byteBuf.writeBytes("hello".getBytes());
        }
    }

    @Test
    public void test32k() {
        System.setProperty("io.netty.allocator.useCacheForAllThreads", "false");
        ByteBuf byteBuf;
        ByteBufAllocator byteBufAllocator = new PooledByteBufAllocator(false);

        byteBuf = byteBufAllocator.buffer(bytes(32));
        byteBuf.writeBytes("hello".getBytes());

        SourceLogger.info(this.getClass(), "======================");
        byteBuf = byteBufAllocator.buffer(bytes(50));
        byteBuf.writeBytes("hello".getBytes());

        SourceLogger.info(this.getClass(), "======================");
        byteBuf = byteBufAllocator.buffer(bytes(32));
        byteBuf.writeBytes("hello".getBytes());
    }




    @Test
    public void testBig() {
        System.setProperty("io.netty.allocator.useCacheForAllThreads", "false");
        ByteBuf byteBuf;
        ByteBufAllocator byteBufAllocator = new PooledByteBufAllocator(false);
        for(int i=0;i<20;++i){
            SourceLogger.info(this.getClass(), "======================"+i);
            byteBuf = byteBufAllocator.buffer(big);
        }
    }
}