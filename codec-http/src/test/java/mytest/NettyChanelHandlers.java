package mytest;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 * 定义了一些通用的Handler
 *
 * @author bairen
 * @description
 **/
public class NettyChanelHandlers {

    private static final Logger log= LoggerFactory.getLogger(NettyChanelHandlers.class);

    private static ExecutorService executor = Executors.newFixedThreadPool(10);

    public static final ChannelHandler ECHO_HANDLER = echoHandler();

    public static final ChannelHandler LOG_HANDLER = logHandler();

    public static final ChannelHandler HTTP_HANDLER = httpHandler(false);

    public static final ChannelHandler HTTP_ASYNC_HANDLER = httpHandler(true);
    //复用event loop
    public static final ChannelHandler REUSE_EVENT_LOOP_HANDLER = reuseEventLoopHandler();

    @Sharable
    public static class EchoHandler extends SimpleChannelInboundHandler<Object>{
        protected void channelRead0(ChannelHandlerContext ctx, Object obj) throws Exception {

            /**
             * 1、buf的实现类是PooledSlicedByteBuf
             * 2、从buf读取字符串有几种方式：
             *    a. buf.toString(CharsetUtil.ISO_8859_1) 推荐
             *    b. buf.readByte(byte[]) 需要配合buf.resetReaderIndex()
             * 3、writeAndFlush()会release()， SimpleChannelInboundHandler 会再次release()，所以这里调用retain()
             */

            //byte[] bytes = new byte[buf.readableBytes()];
            //buf.readBytes(bytes);
            //log.info("receive channelRead:" + new String(bytes));
            //buf.resetReaderIndex();
            if (obj instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) obj;
                log.info("Received data " + buf.toString(CharsetUtil.US_ASCII));
                ctx.writeAndFlush(buf.retain());
                return;
            }
            String data = obj.toString();
            log.info("Received data " + data);
            ctx.writeAndFlush(Unpooled.wrappedBuffer(data.getBytes()));
        }
    }
    private static ChannelHandler echoHandler() {
        return new EchoHandler();
    }

    private static ChannelHandler logHandler() {
        return new SimpleChannelInboundHandler<Object>() {
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                log.info("Received data from baidu");
            }
        };
    }

    @Sharable
    public static class HttpRequestHandler extends ChannelDuplexHandler {
        private boolean async;

        public HttpRequestHandler(boolean async) {
            this.async = async;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
            try{
                channelRead0(ctx,obj);
            }finally {
                ReferenceCountUtil.release(obj);
            }

        }

        protected void channelRead0(ChannelHandlerContext ctx, Object obj) throws Exception {
            FullHttpRequest request = (FullHttpRequest) obj;
//            log.info("Receive uri: {}", request.uri());
//            log.info("Receive content: {}", request.content().toString(CharsetUtil.UTF_8));
//            log.info("request.content().alloc: {}", request.content());

            if (!async) {
                NettyUtil.writeOk(ctx);
                return;
            }
            executor.submit(() -> {
                log.info("wait ");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //再次获取request内容会报错,request已经被销毁
                try {
                    log.info("Receive content: {}", request.content().toString(CharsetUtil.UTF_8));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                NettyUtil.writeOk(ctx);
            });
        }
    }

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        log.info("execute write....");

        ctx.write(msg, promise);
    }

    public static ChannelHandler httpHandler(boolean async) {
        return new HttpRequestHandler(async);
    }

    private static ChannelHandler asyncHttpHandler() {

        return new SimpleChannelInboundHandler<Object>() {
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                FullHttpRequest request = (FullHttpRequest) msg;
                log.info("Receive uri: {}", request.uri());
                log.info("Receive content: {}", request.content().toString(CharsetUtil.UTF_8));


            }
        };
    }

    private static ChannelHandler reuseEventLoopHandler() {
        return new SimpleChannelInboundHandler<FullHttpRequest>() {
            ChannelFuture connectFuture;

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                Bootstrap client = NettyBuilder.builder()
                        .handlers(NettyChanelHandlers.LOG_HANDLER)
                        .buildClient(ctx.channel().eventLoop()); //复用了eventLoop
                connectFuture = client.connect(new InetSocketAddress("www.baidu.com", 80));
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {

                FullHttpRequest request = (FullHttpRequest) msg;
                log.info("Receive uri: {}", request.uri());
                log.info("Receive content: {}", request.content().toString(CharsetUtil.UTF_8));

                DefaultFullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, request.uri());
                connectFuture.channel().writeAndFlush(httpRequest);
            }
        };
    }

    /**
     * @param size array 大小
     * @param pos  写入ChannelHandler在array中的位置
     * @param type 类型(1-ctx.write(),2-ctx.channel.write())
     * @return
     */
    public static ChannelHandler[] batchHandler(int size, int pos, int type) {
        final char[] code = {'A', 'B', 'C', 'D'};

        if (size > code.length) {
            throw new IllegalArgumentException("size more than " + code.length);
        }
        if (pos >= size) {
            throw new IllegalArgumentException(String.format("pos more than size! pos:%s,size:%s", pos, size));
        }
        if (type != 1 && type != 2) {
            throw new IllegalArgumentException(String.format("type illegal! type:%s", type));
        }

        ChannelHandler[] handlers = new ChannelHandler[size];
        IntStream.range(0, size).forEach(ix -> {

            handlers[ix] = new ChannelDuplexHandler() {

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    ByteBuf buf = (ByteBuf) msg;
                    log.info("InBoundHandler-{} channelRead, receive channelRead:{}", code[ix], (char) buf.readByte());
                    buf.resetReaderIndex();

                    if (ix == pos) {
                        System.out.println("write in " + code[ix]);
                        ByteBuf out = Unpooled.wrappedBuffer("hello".getBytes());

                        ChannelFutureListener close = new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture f) throws Exception {
                                f.channel().close();
                                f.channel().parent().close();
                            }
                        };

                        switch (type) {
                            case 1:
                                ctx.writeAndFlush(out).addListener(close);
                                break;
                            case 2:
                                ctx.channel().writeAndFlush(out).addListener(close);
                                break;
                        }

                    }
                    super.channelRead(ctx, msg);
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    log.info("OutboundHandler-{} write", code[ix]);
                    super.write(ctx, msg, promise);
                }

                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    cause.printStackTrace();
                }
            };
        });
        return handlers;
    }

}
