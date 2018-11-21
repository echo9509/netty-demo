package cn.sh.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

/**
 * @author sh
 */
public class HttpServerHandler extends ChannelInboundHandlerAdapter {

    private final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private final Pattern ALLOW_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

    private String url;

    public HttpServerHandler(String url) {
        this.url = url;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        FullHttpRequest request = (FullHttpRequest) msg;
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST);
            return;
        }
        String uri = request.uri();
        String path = sanitizeUri(uri);
        if (StringUtils.isEmpty(path)) {
            sendError(ctx, FORBIDDEN);
            return;
        }
        File file = new File(path);
        if (!file.exists() || file.isHidden()) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        if (file.isDirectory()) {
            if (uri.startsWith("/")) {
                sendListing(ctx, file);
                return;
            }
            senDirect(ctx, uri + "/");
            return;
        }
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        long length = randomAccessFile.length();
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK);
        HttpUtil.setContentLength(response, length);
        setContentType(response, file);
        ctx.write(response);
        ChannelFuture future;

        future = ctx.write(new ChunkedFile(randomAccessFile, 0, randomAccessFile.length(), 8192), ctx.newProgressivePromise());
        future.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationProgressed(ChannelProgressiveFuture channelProgressiveFuture, long l, long l1) throws Exception {

            }

            @Override
            public void operationComplete(ChannelProgressiveFuture channelProgressiveFuture) throws Exception {
            }
        });
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
    }

    private void setContentType(FullHttpResponse response, File file) {
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();
        response.headers().set(CONTENT_TYPE, fileTypeMap.getContentType(file));
    }

    private void senDirect(ChannelHandlerContext ctx, String url) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, FOUND);
        response.headers().set(LOCATION, url);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendListing(ChannelHandlerContext ctx, File file) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK);
        response.headers().set(CONTENT_TYPE, "text/html;charset=UTF-8");
        StringBuilder builder = new StringBuilder();
        String dirPath = file.getPath();
        builder.append("<!DOCTYPE html>\r\n");
        builder.append("<html><head><title>");
        builder.append(dirPath);
        builder.append(" 目录：");
        builder.append("</title></head><body>\r\n");
        builder.append("<h3>").append(dirPath).append(" 目录：").append("</h3>\r\n");
        builder.append("<ul>");
        builder.append("<li>链接：<a href=\"../\">..</a></li>\r\n");
        for (File childFile : file.listFiles()) {
            if (childFile.isHidden() || !childFile.canRead()) {
                continue;
            }
            String name = childFile.getName();
            if (!ALLOW_FILE_NAME.matcher(name).matches()) {
                continue;
            }
            builder.append("<li>链接：<a href=\"");
            builder.append(name);
            builder.append("\">");
            builder.append(name);
            builder.append("</a></li>\r\n");
        }
        builder.append("</ul></body></html>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(builder.toString(), CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "iso-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }
        if (!uri.startsWith(url)) {
            return null;
        }
        if (!uri.startsWith("/")) {
            return null;
        }
        uri = uri.replace("/", File.separator);
        if (uri.startsWith(".") || uri.endsWith(".") || uri.contains(File.separator + ".")
                || uri.contains("." + File.separator) || INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        return System.getProperty("user.dir") + File.separator + uri;
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().set(CONTENT_TYPE, "text/plain;charset=UTF-8");
        response.content().writeBytes(status.toString().getBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
