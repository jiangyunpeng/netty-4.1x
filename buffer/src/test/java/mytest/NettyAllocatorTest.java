package mytest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PoolArena;
import io.netty.buffer.PoolThreadCache;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.SourceLogger;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内存分配规格：
 * - Tiny [0， 512byte]
 * - Small （512byte， 8KB）
 * - Normal [8KB， 16M]
 * - Huge （16M， Max）
 */
public class NettyAllocatorTest {


    @Test
    public void testNormalizeCapacity() throws Exception {
        PoolArena<ByteBuffer> arena = new PoolArena.DirectArena(null, 0, 0, 9, 999999, 0);

        //当小于512时，把reqCapacity低位清0，保证是16的倍数，再加16,相当于reqCapacity-(reqCapacity/%) +16
        int[] reqCapacities = {20, 50, 100};
        int[] expectedResult = {32, 64, 112}; //都是16的倍数
        for (int i = 0; i < reqCapacities.length; i++) {
            System.out.println(arena.normalizeCapacity(reqCapacities[i]));
        }
        //比如 20=16+16=32， 50=(50-50%16)+16=64, 100=(100-100%16)=112
        System.out.println("========================================");
        //当需要的size>=512时，size向上规格化，及513->1024, 1000->1024

        int[] reqCapacities2 = {513, 1000, 2100};
        int[] expectedResult2 = {1024, 1024, 4096};
        for (int i = 0; i < reqCapacities2.length; i++) {
            System.out.println(arena.normalizeCapacity(reqCapacities2[i]));
        }
    }

    @Test
    public void test_TinyCacheIdx() {
        for (int i = 0; i < 512; ++i) {
            System.out.println(i + "\t" + (i >>> 4)); //分配的idx
        }
    }

    @Test
    public void test101() {
        PooledByteBufAllocator byteBufAllocator = new PooledByteBufAllocator(false);
        //先申请100，再申请400，能否复用？
        ByteBuf byteBuf = null;
        byteBuf = byteBufAllocator.buffer(100);
        byteBuf.release();

        byteBuf = byteBufAllocator.buffer(400);
        byteBuf.release();
    }

    @Test
    public void test102() {
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get();
        //64kb 无法复用ThreadCache,因为ThreadCache最大限制32kb
        ByteBuf byteBuf = null;
        byteBuf = byteBufAllocator.buffer(33 * 1024);
        byteBuf.release();
        System.out.println("========================");
        byteBuf = byteBufAllocator.buffer(33 * 1024);
        byteBuf.release();
    }

    @Test
    public void test103() {
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get();
        //16kb可以通过ThreadCache复用
        ByteBuf byteBuf = null;
        byteBuf = byteBufAllocator.buffer(16 * 1024);
        byteBuf.release();
        System.out.println("========================");
        byteBuf = byteBufAllocator.buffer(16 * 1024);
        byteBuf.release();
    }



    @Test
    public void testByteBuf_discardReadBytes() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false);

        ByteBuf buf = allocator.buffer(10);
        buf.writeBytes("hello".getBytes());
        SourceLogger.info(this.getClass(), "readerIndex={}, writerIndex={}", buf.readerIndex(), buf.writerIndex());

        //读了2个字节
        buf.readBytes(new byte[2]);
        SourceLogger.info(this.getClass(), "readerIndex={}, writerIndex={}", buf.readerIndex(), buf.writerIndex());

        //discardReadBytes()返回了原始的buf，只是修改了readIndex和writeIndex
        ByteBuf resized = buf.discardReadBytes().capacity(buf.readableBytes());
        SourceLogger.info(this.getClass(), "readerIndex={}, writerIndex={}", buf.readerIndex(), buf.writerIndex());
        byte[] data = new byte[resized.capacity()];
        System.out.println(resized.readBytes(data));
        System.out.println(new String(data)); //之前已经读的数据会丢弃
    }


    /**
     * https://github.com/netty/netty/pull/10267
     */
    @Test
    public void testBenchmark() {
        int threadSize = 12;
        PooledByteBufAllocator allocator = NettyAllocator.get();
        ExecutorService es = Executors.newFixedThreadPool(threadSize);
        for (int i = 0; i < threadSize; i++) {
            es.submit(() -> {
                while (true) {
                    ByteBuf buf = read(allocator);
                    process(buf);
                    finish(buf);
                }
            });
        }

        try {
            System.in.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ByteBuf read(PooledByteBufAllocator allocator) {
        //随机分配一个size
        Random random = new Random();
        int reqSize = random.nextInt(64 * 1024);
        return allocator.buffer(reqSize);
    }

    private static void process(ByteBuf buf) throws InterruptedException {
        Thread.sleep(200);
    }

    private static void finish(ByteBuf buf) throws InterruptedException {
        buf.release();
    }

    static class NettyAllocator {
        public static PooledByteBufAllocator get() {
            int nHeapArena = PooledByteBufAllocator.defaultNumHeapArena();
            int pageSize = 8192;
            int maxOrder = 7;
            int tinyCacheSize = PooledByteBufAllocator.defaultTinyCacheSize();//tiny缓存池的大小默认512
            int smallCacheSize = PooledByteBufAllocator.defaultSmallCacheSize();//small缓存池的大小默认256
            int normalCacheSize = PooledByteBufAllocator.defaultNormalCacheSize();//norma缓存池的大小默认64
            PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, nHeapArena, 0, pageSize, maxOrder, tinyCacheSize,
                    smallCacheSize, normalCacheSize, true);
            return allocator;
        }
    }

    @Test
    public void testLog2() {
        System.out.println(PoolThreadCache.log2(4));
        System.out.println(PoolThreadCache.log2(8)+"\t"+Integer.toBinaryString(8));
        System.out.println(PoolThreadCache.log2(16));
        System.out.println(PoolThreadCache.log2(32));
        System.out.println(PoolThreadCache.log2(64));
    }
}
