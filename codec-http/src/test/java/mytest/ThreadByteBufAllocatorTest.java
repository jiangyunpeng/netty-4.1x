package mytest;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadByteBufAllocatorTest {


    static ByteBufAllocator byteBufAllocator;

    static {
        System.setProperty("io.netty.allocator.numHeapArenas", "2");
        byteBufAllocator = new PooledByteBufAllocator(false);
    }

    @Test
    public void test() throws InterruptedException {
        int size = 3;
        Thread[] thread = new Thread[size];
        CountDownLatch latch = new CountDownLatch(size);

        for (int i = 0; i < size; ++i) {
            AtomicInteger id = new AtomicInteger(i);
            thread[i] = new Thread(() -> {
                allocate(id.get());
                latch.countDown();
            });
            thread[i].setName("thread-" + i);
            thread[i].start();
        }

        latch.await();
        System.out.println("end");
    }

    private static void allocate(int i) {
        byteBufAllocator.buffer(1024*20);
    }
}
