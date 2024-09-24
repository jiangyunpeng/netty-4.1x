package mytest;

import mytest.NettyBuilder.Protocol;
import org.junit.Test;

public class NettyTest {

    /**
     * telnet echo line
     */
    @Test
    public void testLine() {
        NettyBuilder.builder()
                .protocol(Protocol.LINE)
                .handlers(NettyChanelHandlers.ECHO_HANDLER)
                .buildServer()
                .start();
    }

    /**
     * telnet echo single
     */
    @Test
    public void test_Single() {
        NettyBuilder.builder()
                .protocol(Protocol.SINGLE)
                .handlers(NettyChanelHandlers.ECHO_HANDLER)
                .buildServer()
                .start();
    }

    /**
     * this is a test => [this,is,a,test]
     */
    @Test
    public void test_Split() {
        NettyBuilder.builder()
                .protocol(Protocol.SPLIT)
                .handlers(NettyChanelHandlers.ECHO_HANDLER)
                .buildServer()
                .start();
    }

    /**
     * http 正常测试
     */
    @Test
    public void test_Http() {
        NettyBuilder.builder()
                .protocol(Protocol.HTTP)
                .handlers(NettyChanelHandlers.HTTP_HANDLER)
                .buildServer()
                .start();
    }

    /**
     * curl http://localhost:8080 -d 'test'
     * 异步发送Response，会触发release异常
     *
     */
    @Test
    public void test_AsyncHttp() {
        NettyBuilder.builder()
                .protocol(Protocol.HTTP)
                .handlers(NettyChanelHandlers.HTTP_ASYNC_HANDLER)
                .buildServer()
                .start();
    }

    /**
     * 复用EventLoop，减少线程切换
     */
    @Test
    public void test_ReuseEventLoop() {

        NettyBuilder.builder()
                .protocol(Protocol.HTTP)
                .handlers(NettyChanelHandlers.REUSE_EVENT_LOOP_HANDLER)
                .buildServer()
                .start();

    }

    /**
     * ctx.write()和ctx.channel.write()区别:
     * A ->B ->C   (in)
     * A <-B <-C   (out)
     * ctx.write 从当前handler(B)发送out事件，ctx.channel.write从尾部发送out事件
     *
     */
    @Test
    public void test_CtxChannelWrite() {
        NettyBuilder.builder()
                .protocol(Protocol.SINGLE)
                .handlers(NettyChanelHandlers.batchHandler(3, 1, 1))
                .buildServer()
                .start();

        System.out.println("========================");

        NettyBuilder.builder()
                .protocol(Protocol.SINGLE)
                .handlers(NettyChanelHandlers.batchHandler(3, 1, 2))
                .buildServer()
                .start();
    }
}
