package mytest;


import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bairen
 * @description
 **/

public class NettyBuilder {
    private static final Logger log= LoggerFactory.getLogger(NettyBuilder.class);

    public static NettyBuilder builder() {
        return new NettyBuilder();
    }

    private static ChannelHandler httpChannelInitializer(ChannelHandler[] channelHandlers) {
        return new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast("codec", new HttpServerCodec());
                ch.pipeline().addLast("aggregator", new HttpObjectAggregator(512 * 1024));
                for (ChannelHandler handler : channelHandlers) {
                    ch.pipeline().addLast("requestHandler",handler);
                }
            }
        };
    }

    private static ChannelHandler singleChannelInitializer(ChannelHandler[] channelHandlers) {

        return new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {

                ByteToMessageDecoder decoder = new ByteToMessageDecoder() {

                    private StringBuffer sb = new StringBuffer();

                    @Override
                    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                        char c = (char) in.readByte();
                        if (c == ' ') {
                            out.add(sb.toString());
                            sb.delete(0, sb.length());
                        } else {
                            sb.append(c);
                        }
                    }
                };

                //单个英文字母\r\n
                ch.pipeline().addLast("codec", decoder);

                for (ChannelHandler handler : channelHandlers) {
                    ch.pipeline().addLast(handler);
                }
            }
        };
    }

    private static class MessageDecoder extends ByteToMessageDecoder{
        //0表示消息开始, 1表示读取中
        int state = 0;

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            StringBuffer sb = new StringBuffer();
            while (in.isReadable()) {
                switch (state) {
                    case 0: {//开始
                        char c = (char) in.readByte();
                        out.add("@" + c);
                        state = 1;
                        break;
                    }
                    case 1: { //读取
                        byte c = in.readByte();
                        //读取到换行结束
                        if (c == '\r' && in.readByte() == '\n') {
                            sb.append(";");
                            out.add(sb.toString());
                            //结束需要重置
                            state = 0;
                            return;
                        }
                        if (c == ' ') {
                            out.add(sb.toString());
                            return;
                        }
                        sb.append((char) c);
                        break;
                    }
                }

            }
        }
    }
    private static class MessageAggregator extends MessageToMessageDecoder<String> {

        private List<String> list;

        //判断是否开始
        private boolean isStartMessage(String msg) {
            return msg.indexOf("@") != -1;
        }

        //判断是否结束
        private boolean isEndMessage(String msg) {
            return msg.indexOf(";") != -1;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, String msg, List out) throws Exception {
            if (isStartMessage(msg)) {
                list = new ArrayList<>();
            }
            list.add(msg);
            if (isEndMessage(msg)) {
                out.add(list);
                return;
            }
        }
    }

    private static ChannelHandler splitChannelInitializer(ChannelHandler[] channelHandlers) {
        return new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {

                //注意不能是单例，多个channel会抛出异常
                ch.pipeline()
                        .addLast("decoder", new MessageDecoder())
                        .addLast("aggregator", new MessageAggregator());

                for (ChannelHandler handler : channelHandlers) {
                    ch.pipeline().addLast(handler);
                }
            }
        };
    }

    private static ChannelHandler lineChannelInitializer(ChannelHandler[] channelHandlers) {
        return new ChannelInitializer() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                //单个英文字母\r\n
                ch.pipeline().addLast("codec", new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Unpooled.wrappedBuffer("\r\n".getBytes())));

                for (ChannelHandler handler : channelHandlers) {
                    ch.pipeline().addLast(handler);
                }
            }
        };
    }

    //协议
    public enum Protocol {
        EMPTY{
            ChannelHandler initChannelHandler(ChannelHandler[] channelHandlers) {
                return new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        for (ChannelHandler handler : channelHandlers) {
                            ch.pipeline().addLast(handler);
                        }
                    }
                };
            }
        },
        HTTP {
            ChannelHandler initChannelHandler(ChannelHandler[] channelHandlers) {
                return httpChannelInitializer(channelHandlers);
            }
        },
        LINE {
            //按行
            ChannelHandler initChannelHandler(ChannelHandler[] channelHandlers) {
                return lineChannelInitializer(channelHandlers);
            }
        },
        SINGLE {
            //1个byte
            ChannelHandler initChannelHandler(ChannelHandler[] channelHandlers) {
                return singleChannelInitializer(channelHandlers);
            }
        },
        SPLIT {
            ChannelHandler initChannelHandler(ChannelHandler[] channelHandlers) {
                return splitChannelInitializer(channelHandlers);
            }
        };

        abstract ChannelHandler initChannelHandler(ChannelHandler[] channelHandlers);

    }

    private int port = 8080;

    private Protocol protocol = Protocol.EMPTY;

    private ServerBootstrap server;

    private Channel serverChannel;

    private Bootstrap client;

    private ChannelHandler[] channelHandlers;

    public NettyBuilder protocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public NettyBuilder handlers(ChannelHandler... channelHandlers) {
        this.channelHandlers = channelHandlers;
        return this;
    }

    public Bootstrap client() {
        return client;
    }

    public Bootstrap buildClient() {
        return buildClient(new NioEventLoopGroup());
    }

    public Bootstrap buildClient(EventLoopGroup eventLoopGroup) {
        client = new Bootstrap();
        client.group(eventLoopGroup);
        client.channel(NioSocketChannel.class);
        client.handler(protocol.initChannelHandler(channelHandlers));

        return client;
    }

    public NettyBuilder buildServer() {
        server = new ServerBootstrap();
        server.group(new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(NioChannelOption.SO_KEEPALIVE, true);
        //.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        server.childHandler(protocol.initChannelHandler(channelHandlers));
        return this;
    }

    public void start() {
        ChannelFuture future = server.bind(port);
        log.info("Server Started on {}", port);
        // 等待close事件
        try {
            serverChannel = future.channel();
            serverChannel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
