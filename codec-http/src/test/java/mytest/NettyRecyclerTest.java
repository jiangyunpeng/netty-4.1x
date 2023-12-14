package mytest;

import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

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
    public void test1() {
        PooledConnection pooledConnection = PooledConnection.getOrCreate();
        pooledConnection.recycle();//回收
        PooledConnection.getOrCreate();//再次使用
    }

    @Test
    public void test2() throws InterruptedException, IOException {
        List<List<PooledConnection>> list = new ArrayList();
        int threadSize = 3;
        int elementSize = 10;//

        //① 创建线程负责创建对象
        IoThread ioThread = new IoThread();
        ioThread.start();
        CountDownLatch createWaiter = new CountDownLatch(1);
        ioThread.addTask(() -> {
            for (int i = 0; i < threadSize; ++i) {
                List<PooledConnection> elements = new ArrayList<>();
                list.add(elements);
                for (int j = 0; j < elementSize; ++j) {
                    elements.add(PooledConnection.getOrCreate());
                }

            }
            createWaiter.countDown();
        });
        createWaiter.await();

        //② n个回收线程回收对象，这里存在并发
        Thread[] threads = new Thread[threadSize];
        for (int i = 0; i < threadSize; ++i) {
            final int current = i;
            threads[i] = new Thread(() -> {
                List<PooledConnection> pooledConnectionList = list.get(current);
                log.info("try recycle {} :{}", pooledConnectionList.size(), pooledConnectionList.toString());
                for (PooledConnection connection : pooledConnectionList) {
                    connection.recycle();
                }
            });
            threads[i].setName("thread-" + current);
            threads[i].start();
            threads[i].join();//这里为了方便观察，让connection整体有序故意加的
        }
        threads = null;
        System.gc();
        Thread.sleep(1000);
        System.gc();
        System.out.println("====================");
        //③ 创建线程再次获取对象
        CountDownLatch getLatch = new CountDownLatch(1);
        ioThread.addTask(() -> {
            log.info("get: " + PooledConnection.getOrCreate().toString());
            log.info("get: " + PooledConnection.getOrCreate().toString());
            log.info("get: " + PooledConnection.getOrCreate().toString());
            getLatch.countDown();
        });
        getLatch.await();
        ioThread.shutdown();

    }


    /**
     * 30个线程，每个线程回收一个，对应的模型：
     * weakOrderQueue1-> weakOrderQueue2 -> weakOrderQueue3 -> ...
     */
    @Test
    public void test3() throws InterruptedException {
        List<PooledConnection> pooledConnectionList = new ArrayList<>();
        newInstance(30, pooledConnectionList::add);
        for (int i = 0; i < 30; ++i) {
            final int current = i;
            Thread thread = new Thread(() -> {
                PooledConnection connection = pooledConnectionList.get(current);
                connection.recycle();
            });
            thread.setName("thread-" + current);
            thread.start();
            thread.join();//这里为了方便观察，让connection整体有序故意加的
        }

        System.out.println("get: " + PooledConnection.getOrCreate().toString());
        System.out.println("get: " + PooledConnection.getOrCreate().toString());
        System.out.println("get: " + PooledConnection.getOrCreate().toString());
    }


    static class RecycleThread extends Thread {
        ArrayBlockingQueue<PooledConnection> queue = new ArrayBlockingQueue<>(128);

        public RecycleThread(String name) {
            super(name);
            this.start();
        }

        @Override
        public void run() {
            while (true) {
                PooledConnection connection = null;
                try {
                    connection = queue.take();
                    if (connection == PooledConnection.NULL) {
                        break;
                    }
                    System.out.println("receive: " + connection);
                    connection.recycle();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("goodbye!");
        }

        public void add(PooledConnection connection) {
            queue.add(connection);
        }

    }

    /**
     * 模拟回收线程被gc回收的情况，需要debug才能复现出这种场景
     *
     * @throws InterruptedException
     */
    @Test
    public void test4() throws InterruptedException {

        Object object = new Object();
        RecycleThread recycleThread1 = new RecycleThread("thread-1");
        RecycleThread recycleThread2 = new RecycleThread("thread-2");
        newInstance(2, recycleThread1.queue::add);
        newInstance(2, recycleThread2.queue::add);
        Thread.sleep(100);



        //get connection-1
//        PooledConnection.getOrCreate();
//        System.out.println("==========2======");
//        //create connection-3 and connection-4
//        newInstance(1, queue::add);
//        //gc
//        queue.add(PooledConnection.NULL);
//        thread = null;
//        System.gc();
//        Thread.sleep(1000);
//        System.gc();
//        //recycle connection-9
//        PooledConnection.getOrCreate();

    }


    private void newInstance(int size, Consumer<PooledConnection> consumer) {
        for (int i = 0; i < size; ++i) {
            consumer.accept(PooledConnection.getOrCreate());
        }
    }


    /**
     * 模仿 io.netty.buffer.PooledHeapByteBuf 通过Recycler实现一个连接池
     */
    private static class PooledConnection {

        private static final AtomicInteger ID_GENERATOR = new AtomicInteger();
        public static final PooledConnection NULL = new PooledConnection();

        private static Recycler<PooledConnection> RECYCLER = new Recycler<PooledConnection>() {
            @Override
            protected PooledConnection newObject(Handle<PooledConnection> handle) {
                return new PooledConnection(handle);
            }
        };

        public static PooledConnection getOrCreate() {
            return RECYCLER.get();
        }

        //recyclerHandle用于回收对象
        private final Handle<PooledConnection> recyclerHandle;
        private final int id;

        private PooledConnection() {
            this.recyclerHandle = null;
            this.id = -1;
        }

        private PooledConnection(Handle<PooledConnection> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
            id = ID_GENERATOR.incrementAndGet();
            //log.info("create {} with {}", this, recyclerHandle);
        }

        public void recycle() {
            //log.info("recycle {} with {}", this, recyclerHandle);
            recyclerHandle.recycle(this);
        }

        @Override
        public String toString() {
            return "connection-" + (id >= 0 ? id : "null");
        }
    }


    @Deprecated
    private static Executor getExecutor(int threadSize) {
        AtomicInteger counter = new AtomicInteger();
        Executor es = Executors.newFixedThreadPool(threadSize, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread workThread = new Thread(r);
                workThread.setName("thread-" + counter.incrementAndGet());
                return workThread;
            }
        });
        return es;
    }

    static class IoThread extends Thread {
        ArrayBlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(128);
        AtomicBoolean stopped = new AtomicBoolean();

        public IoThread() {
            super("ioThread");
        }

        @Override
        public void run() {
            while (!stopped.get()) {
                try {
                    Runnable task = tasks.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            log.info("goodbye IoThread!");
        }

        public void addTask(Runnable task) {
            tasks.add(task);
        }

        @Override
        public void start() {
            super.start();
        }

        public void shutdown() {
            stopped.set(true);
        }
    }
}
