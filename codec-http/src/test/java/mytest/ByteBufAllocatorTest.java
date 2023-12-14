package mytest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.Test;

public class ByteBufAllocatorTest {

    private int kb_16 = 1024 * 16;
    private int kb_8 = 1024 * 8;

    @Test
    public void test() {
        ByteBufAllocator byteBufAllocator = new PooledByteBufAllocator(false);
        ByteBuf byteBuf1 = byteBufAllocator.buffer();
        ByteBuf byteBuf2 = byteBufAllocator.buffer(kb_8);
        ByteBuf byteBuf3 = byteBufAllocator.buffer(kb_16);

        byteBuf1.release();
    }
}
