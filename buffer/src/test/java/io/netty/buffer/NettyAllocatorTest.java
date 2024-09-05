package io.netty.buffer;

import org.junit.Test;

public class NettyAllocatorTest {


    private int kb_16 = 1024 * 16;
    private int kb_8 = 1024 * 8;
    private int tiny = 100;

    @Test
    public void test() {
        PooledByteBufAllocator byteBufAllocator = new PooledByteBufAllocator(false);

        ByteBuf byteBuf = null;

        byteBuf = byteBufAllocator.buffer(tiny);
        byteBuf.release();

        byteBuf = byteBufAllocator.buffer(tiny);
        byteBuf.release();
    }
}
