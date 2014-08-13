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

import java.net.URI;
import java.util.Arrays;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2OrHttpChooser.SelectedProtocol;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.Promise;

public final class Http2Client implements AutoCloseable {

  private final String host;
  private final int port;
  private final boolean ssl;

  private final SslContext sslCtx;
  private final Http2ClientConnectionHandler connectionHandler;
  private final NioEventLoopGroup workerGroup;
  private final Channel channel;

  public Http2Client(final URI uri) throws Exception {
    this(uri.getHost(), uri.getPort(), "https".equals(uri.getScheme()));
  }

  public Http2Client(final String host, final int port, final boolean ssl) throws Exception {
    this.host = host;
    this.port = port;
    this.ssl = ssl;

    // Configure SSL.
    if (ssl) {
      this.sslCtx = SslContext.newClientContext(
          null, InsecureTrustManagerFactory.INSTANCE, null,
          Arrays.asList(SelectedProtocol.HTTP_2.protocolName(),
                        SelectedProtocol.HTTP_1_1.protocolName()),
          0, 0);
    } else {
      this.sslCtx = null;
    }

    // XXX (dano): Http2Connection does not seem to be thread safe, use one thread only
    this.workerGroup = new NioEventLoopGroup(1);
    Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx);

    // Configure the client.
    Bootstrap b = new Bootstrap();
    b.group(workerGroup);
    b.channel(NioSocketChannel.class);
    b.option(ChannelOption.SO_KEEPALIVE, true);
    b.remoteAddress(host, port);
    b.handler(initializer);

    // Start the client.
    this.channel = b.connect().syncUninterruptibly().channel();
    System.out.println("Connected to [" + host + ':' + port + ']');

    // Wait for the HTTP/2 upgrade to occur.
    this.connectionHandler = initializer.connectionHandler();
    connectionHandler.awaitInitialization();
  }

  @Override
  public void close() {
    channel.close().syncUninterruptibly();
    workerGroup.shutdownGracefully();
  }

  public Promise<FullHttpResponse> send(final FullHttpRequest request) {
    return connectionHandler.send(request);
  }
}
