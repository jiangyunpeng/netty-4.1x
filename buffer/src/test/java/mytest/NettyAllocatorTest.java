package mytest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PoolArena;
import io.netty.buffer.PoolThreadCache;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.toolkit.FormatUtils;
import io.netty.util.SourceLogger;
import io.netty.util.internal.LongCounter;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

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
        System.out.println("==================small======================");
        //当需要的size>=512时，size向上规格化，及513->1024, 1000->1024

        int[] reqCapacities2 = {1024+1, 1024*2+1, 1024*4+1};
        for (int i = 0; i < reqCapacities2.length; i++) {
            System.out.println(reqCapacities2[i] + "=>" + arena.normalizeCapacity(reqCapacities2[i]));
        }
        System.out.println("===================normal=====================");
        int[] reqCapacities3 = {1024 * 17, 1024 * 70};
        for (int i = 0; i < reqCapacities3.length; i++) {
            System.out.println(FormatUtils.humanReadableByteSize(reqCapacities3[i]) + "=>"
                    + FormatUtils.humanReadableByteSize(arena.normalizeCapacity(reqCapacities3[i])));
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
        //chunk容量只有128kb
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(4);
        //测试相同规格的ThreadCache复用
        ByteBuf byteBuf = null;
        byteBuf = byteBufAllocator.buffer(16);
        byteBuf.release();
        System.out.println("========================");
        byteBuf = byteBufAllocator.buffer(16);
        byteBuf.release();
    }

    @Test
    public void test102() {
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get();
        //64kb 无法复用ThreadCache,因为ThreadCache最大限制32kb
        ByteBuf byteBuf = null;
        byteBuf = byteBufAllocator.buffer(64 * 1024);
        byteBuf.release();
        System.out.println("========================");
        byteBuf = byteBufAllocator.buffer(64 * 1024);
        byteBuf.release();
    }

    @Test
    public void test103() {
        //chunk容量只有128kb
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(4);
        //测试16kb无法从32kb的子节点分配
        ByteBuf byteBuf = null;
        byteBuf = byteBufAllocator.buffer(32 * 1024);
        byteBuf = byteBufAllocator.buffer(16 * 1024);
        byteBuf = byteBufAllocator.buffer(512);
        byteBuf = byteBufAllocator.buffer(7 * 1024);
    }

    @Test
    public void testNormal() {
        //chunk容量只有64kb，二叉树数组结构[64，32，32,16,16,16,16,8,8...]
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(3,false);
        //申请8k
        ByteBuf byteBuf1= byteBufAllocator.buffer(1024*8);
        //申请16k
        ByteBuf byteBuf2= byteBufAllocator.buffer(1024*10);
        //申请8k
        ByteBuf byteBuf3= byteBufAllocator.buffer(1024*8);

        byteBuf1.release();
        byteBuf3.release();

    }

    @Test
    public void testTiny() {
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(11);
        //先申请一个128，会创建128规格的PoolSubpage
        //再申请一个16，是否会复用128规格的PoolSubpage？
        //答案是不会，能否在同一个Subpage中分配，是通过PoolArena中的PoolSubpage数组确定，不同规格会路由到不同PoolSubpage
        byteBufAllocator.buffer(64);
        byteBufAllocator.buffer(16);
    }

    @Test
    public void testSmall() {
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(4,false);
        //回收的PoolSubpage如何加入到链表

        ByteBuf byteBuf1= byteBufAllocator.buffer(4*1024);
        ByteBuf byteBuf2= byteBufAllocator.buffer(4*1024);//PoolSubpage不够用，会被移除
        ByteBuf byteBuf3=byteBufAllocator.buffer(4*1024);//产生新的PoolSubpage
        byteBuf1.release();
        byteBuf2.release();

        byteBufAllocator.buffer(4*1024);
    }

    @Test
    public void testThreadLocalCache() {
        //1mb
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(7);
        //如果和使用 ThreadLocalCache

        ByteBuf byteBuf1= byteBufAllocator.buffer(32*1024);
        byteBuf1.release();
        ByteBuf byteBuf2= byteBufAllocator.buffer(32*1024);
        byteBuf2.release();
    }

    @Test
    public void testReallocate() {
        //chunk容量只有128kb
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(4);
        ByteBuf byteBuf = byteBufAllocator.buffer(32 * 1024);
        byteBuf = byteBuf.discardReadBytes().capacity(406);
        byteBuf.release();

    }

    /**
     * 测试申请<512的内存分配情况
     */
    @Test
    public void testBenchmarkTiny() throws IOException {
        //chunk容量只有1mb
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(7);
        Random random = new Random();
        AtomicLong counter = new AtomicLong();
        int count = 0;
        while (true) {
            int reqSize = 16 + random.nextInt(496 - 16); //16~496之间的随机数,如果16~512可能产生511的随机数属于small
            counter.addAndGet(reqSize);
            byteBufAllocator.buffer(reqSize);
            ++count;
            if (counter.get() >= 1024 * 1024 * 3) {
                break;
            }
        }
        System.out.println(count);
        System.out.println("==============");
        System.in.read();
    }

    /**
     * 测试8k~1mb的内存分配情况
     */
    @Test
    public void testBenchmarkNormal() throws IOException {
        //chunk容量只有1mb
        PooledByteBufAllocator byteBufAllocator = NettyAllocator.get(11);
        Random random = new Random();
        AtomicLong counter = new AtomicLong();
        int count = 0;
        while (true) {
            int pageSize = 1024 * 8;
            int chunkSize = 1024 * 1024;
            int reqSize = pageSize + random.nextInt(chunkSize - pageSize);
            counter.addAndGet(reqSize);
            byteBufAllocator.buffer(reqSize);
            ++count;
            if (counter.get() >= 1024 * 1024 * 100) {
                break;
            }
        }
        System.out.println(count);
        System.out.println("==============");
        System.in.read();


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
            return get(7);//default 1mb= 8k<<7
            // 128kb= 8k<<4
        }

        public static PooledByteBufAllocator get(int maxOrder) {
            return get(maxOrder,true);
        }

        public static PooledByteBufAllocator get(int maxOrder,boolean useCache) {
            int nHeapArena = PooledByteBufAllocator.defaultNumHeapArena();
            int pageSize = 8192;
            int tinyCacheSize = PooledByteBufAllocator.defaultTinyCacheSize();//tiny缓存池的大小默认512
            int smallCacheSize = PooledByteBufAllocator.defaultSmallCacheSize();//small缓存池的大小默认256
            int normalCacheSize = PooledByteBufAllocator.defaultNormalCacheSize();//norma缓存池的大小默认64
            PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, nHeapArena, 0, pageSize, maxOrder, tinyCacheSize,
                    smallCacheSize, normalCacheSize, useCache);
            return allocator;
        }
    }

    /**
     * 测试2为底的对数
     */
    @Test
    public void testLog2() {
        System.out.println(PoolThreadCache.log2(4));
        System.out.println(PoolThreadCache.log2(8) + "\t" + Integer.toBinaryString(8));
        System.out.println(PoolThreadCache.log2(16));
        System.out.println(PoolThreadCache.log2(32));
        System.out.println(PoolThreadCache.log2(64));
    }

    @Test
    public void testRunLength() {
        //PoolChunk.runLength()
        int runLength = 1 << 20 - 4;
        System.out.println(runLength);
    }
}
