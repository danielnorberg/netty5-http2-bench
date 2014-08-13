/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2InboundFlowController;
import io.netty.handler.codec.http2.DefaultHttp2OutboundFlowController;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.internal.logging.InternalLogLevel.INFO;

/**
 * A subclass of the connection handler that interprets response messages as text and prints it out
 * to the console.
 */
public class Http2ClientConnectionHandler extends AbstractHttp2ConnectionHandler {

  private static final Http2FrameLogger logger = new Http2FrameLogger(
      INFO, InternalLoggerFactory.getInstance(Http2ClientConnectionHandler.class));

  private final ChannelPromise initPromise;
  private ByteBuf collectedData;
  private ChannelHandlerContext ctx;
  private ConcurrentMap<Integer, OutstandingRequest> outstanding = new ConcurrentHashMap<>();

  private class OutstandingRequest {

    private HttpResponseStatus status;
    private Promise<FullHttpResponse> promise = ctx.executor().newPromise();
    private HttpVersion version = HTTP_1_1;

    public void finish(final ByteBuf content) {
      final FullHttpResponse response = new DefaultFullHttpResponse(version, status,
                                                                    content.retain());
      promise.setSuccess(response);
    }
  }

  public Http2ClientConnectionHandler(ChannelPromise initPromise) {
    this(initPromise, new DefaultHttp2Connection(false));
  }

  private Http2ClientConnectionHandler(ChannelPromise initPromise, Http2Connection connection) {
    super(connection, new DefaultHttp2FrameReader(), new DefaultHttp2FrameWriter(),
          new DefaultHttp2InboundFlowController(
              connection), new DefaultHttp2OutboundFlowController(connection));
    this.initPromise = initPromise;
  }

  /**
   * Wait for this handler to be added after the upgrade to HTTP/2, and for initial preface
   * handshake to complete.
   */
  public void awaitInitialization() throws Exception {
    if (!initPromise.awaitUninterruptibly(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timed out waiting for initialization");
    }
    if (!initPromise.isSuccess()) {
      throw new RuntimeException(initPromise.cause());
    }
  }

  /**
   * Handles conversion of a {@link FullHttpMessage} to HTTP/2 frames.
   */
  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
    if (msg instanceof FullHttpMessage) {
      FullHttpMessage httpMsg = (FullHttpMessage) msg;
      boolean hasData = httpMsg.content().isReadable();

      // Convert and write the headers.
      DefaultHttp2Headers.Builder headers = DefaultHttp2Headers.newBuilder();
      for (Map.Entry<String, String> entry : httpMsg.headers().entries()) {
        headers.add(entry.getKey(), entry.getValue());
      }
      int streamId = nextStreamId();
      writeHeaders(ctx, promise, streamId, headers.build(), 0, !hasData, false);
      if (hasData) {
        writeData(ctx, ctx.newPromise(), streamId, httpMsg.content(), 0, true, true);
      }
    } else {
      ctx.write(msg, promise);
    }
  }

  private volatile int streamIdCounter = 1;

  public Promise<FullHttpResponse> send(final FullHttpRequest request) {
    boolean hasData = request.content().isReadable();

    // Convert and write the headers.
    DefaultHttp2Headers.Builder headers = DefaultHttp2Headers.newBuilder();
    for (Map.Entry<String, String> entry : request.headers().entries()) {
      headers.add(entry.getKey(), entry.getValue());
    }
    synchronized (this) {
      streamIdCounter += 2;
      final int streamId = streamIdCounter;
//      System.err.println("send: " + streamId);
      final OutstandingRequest outstandingRequest = new OutstandingRequest();
      outstanding.put(streamId, outstandingRequest);
      writeHeaders(ctx, ctx.newPromise(), streamId, headers.build(), 0, !hasData, false);
      if (hasData) {
        writeData(ctx, ctx.newPromise(), streamId, request.content(), 0, true, true);
      }
      return outstandingRequest.promise;
    }
  }

  @Override
  public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                         boolean endOfStream, boolean endOfSegment) throws Http2Exception {

    // Copy the data into the buffer.
    int available = data.readableBytes();
    if (collectedData == null) {
      collectedData = ctx().alloc().buffer(available);
      collectedData.writeBytes(data, data.readerIndex(), data.readableBytes());
    } else {
      // Expand the buffer
      ByteBuf newBuffer = ctx().alloc().buffer(collectedData.readableBytes() + available);
      newBuffer.writeBytes(collectedData);
      newBuffer.writeBytes(data);
      collectedData.release();
      collectedData = newBuffer;
    }

    // If it's the last frame, print the complete message.
    if (endOfStream) {
//      System.out.println("Received message: " + collectedData.toString(CharsetUtil.UTF_8));

      final OutstandingRequest outstandingRequest = outstanding.remove(streamId);
      outstandingRequest.finish(collectedData);

      // Free the data buffer.
      collectedData.release();
      collectedData = null;
    }
  }

  @Override
  public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                            int streamDependency, short weight, boolean exclusive, int padding,
                            boolean endStream,
                            boolean endSegment) throws Http2Exception {
    if (headers.contains(Http2ExampleUtil.UPGRADE_RESPONSE_HEADER)) {
      System.out.println("Received HTTP/2 response to the HTTP->HTTP/2 upgrade request");
    }
    final OutstandingRequest outstandingRequest = outstanding.get(streamId);
    if (outstandingRequest != null) {
      for (Map.Entry<String, String> entry : headers.entries()) {
        if (outstandingRequest.status == null && entry.getKey().equals(":status")) {
          outstandingRequest.status = HttpResponseStatus.valueOf(Integer.valueOf(entry.getValue()));
        }
      }
    }
  }

  @Override
  public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
      throws Http2Exception {
    this.ctx = ctx;
    if (!initPromise.isDone()) {
      initPromise.setSuccess();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    if (!initPromise.isDone()) {
      initPromise.setFailure(cause);
    }
    super.exceptionCaught(ctx, cause);
  }
}
