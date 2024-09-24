package mytest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/**
 * @author bairen
 * @description
 **/
public class NettyUtil {

    public static void writeOk(ChannelHandlerContext ctx){
        HttpVersion version = HttpVersion.HTTP_1_1;
        HttpResponseStatus status = HttpResponseStatus.OK;
        ByteBuf content = Unpooled.copiedBuffer("OK", CharsetUtil.UTF_8);

        FullHttpResponse response = new DefaultFullHttpResponse(version, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}
