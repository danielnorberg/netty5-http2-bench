import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.concurrent.Promise;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.CharsetUtil.UTF_8;

public class Benchmark {

  private final URI uri;
  private final Integer concurrency;

  private ProgressMeter meter;

  public Benchmark(final String... args) {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("Netty5 Http2 Benchmark")
        .defaultHelp(true);
    parser.addArgument("--uri")
        .setDefault("http://127.0.0.1:8080");
    parser.addArgument("-c", "--concurrency")
        .setDefault(10);
    Namespace ns = null;
    try {
      ns = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.exit(1);
    }
    this.uri = URI.create(ns.getString("uri"));
    this.concurrency = ns.getInt("concurrency");
  }

  public static void main(final String... args) throws Exception {
    Executors.newSingleThreadExecutor().submit(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        Http2Server.main();
        return null;
      }
    });

    Thread.sleep(1000);

    new Benchmark(args).run();
  }

  private void run() throws Exception {
    this.meter = new ProgressMeter();

    final Http2Client client = new Http2Client(uri);
    final Queue<Request> requests = new ArrayDeque<>();

    for (int i = 0; i < concurrency; i++) {
      requests.add(Request.send(client));
    }

    while (true) {
      final Request request = requests.poll();
      request.response.sync().get().release();
      requests.add(Request.send(client));
      meter.inc(1, request.durationNanos());
    }
  }

  private final static class Request {
    private final long startNanos;
    private final Promise<FullHttpResponse> response;

    private Request(final long startNanos, final Promise<FullHttpResponse> response) {
      this.startNanos = startNanos;
      this.response = response;
    }


    public long durationNanos() {
      return System.nanoTime() - startNanos;
    }

    public static Request send(final Http2Client client) {
      final ByteBuf content = copiedBuffer("sample data".getBytes(UTF_8));
      final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST, "/foo", content);
      request.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/plain");
      final Promise<FullHttpResponse> response = client.send(request);
      return new Request(System.nanoTime(), response);
    }
  }
}
