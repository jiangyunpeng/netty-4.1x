package mytest;

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author bairen
 * @description
 **/
public class NettyRecyclerTest {

    private static final Logger log = LoggerFactory.getLogger(NettyRecyclerTest.class);

    /**
     * 回收频率由参数 RATIO 控制，默认每创建 8 个对象最后只回收 1 个对象
     */
    @Test
    public void testBasic() {
        PooledConnection pooledConnection = PooledConnection.newInstance();
        pooledConnection.recycle();//回收
        PooledConnection.newInstance();//再次使用
    }

    @Test
    public void testMultiThread() throws InterruptedException {
        //① 创建线程main创建对象
        List<PooledConnection> list = new ArrayList();
        for (int i = 0; i < 30; ++i) {
            list.add(PooledConnection.newInstance());
        }
        CountDownLatch wait = new CountDownLatch(30);

        //② 三个回收线程回收对象，这里存在并发
        AtomicInteger counter = new AtomicInteger();
        ExecutorService es = Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread workThread = new Thread(r);
                workThread.setName("thread-" + counter.incrementAndGet());
                return workThread;
            }
        });

        for (int i = 0; i < list.size(); ++i) {
            final PooledConnection connection = list.get(i);
            es.submit(() -> {
                connection.recycle();
                wait.countDown();
            });
        }
        wait.await();
        es.shutdownNow();
        System.gc();
        //③ 创建线程再次获取对象
        log.info(PooledConnection.newInstance().toString());
        log.info(PooledConnection.newInstance().toString());
        log.info(PooledConnection.newInstance().toString());

    }

    /**
     * 模仿 io.netty.buffer.PooledHeapByteBuf 通过Recycler实现一个连接池
     */
    private static class PooledConnection {

        private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

        private static Recycler<PooledConnection> RECYCLER = new Recycler<PooledConnection>() {
            @Override
            protected PooledConnection newObject(Handle<PooledConnection> handle) {
                return new PooledConnection(handle);
            }
        };

        public static PooledConnection newInstance() {
            return RECYCLER.get();
        }

        //recyclerHandle用于回收对象
        private final Handle<PooledConnection> recyclerHandle;
        private final int id;

        private PooledConnection(Handle<PooledConnection> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
            id = ID_GENERATOR.incrementAndGet();
            log.info("create {} with {}", this,recyclerHandle);
        }

        public void recycle() {
            log.info("recycle {} with {}", this, recyclerHandle);
            recyclerHandle.recycle(this);
        }

        @Override
        public String toString() {
            return "connection-" + id;
        }
    }
}
