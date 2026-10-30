package cz.cyberrange.platform.guacamole.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-process HTTP server answering fixed responses per path and recording request headers. */
public final class StubHttpServer implements AutoCloseable {

  private final HttpServer server;
  private final Map<String, String> lastRequestHeaders = new ConcurrentHashMap<>();

  public StubHttpServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.start();
  }

  public StubHttpServer respond(String path, int status, String contentType, String body) {
    try {
      server.removeContext(path);
    } catch (IllegalArgumentException ignored) {
      // no earlier response registered for the path
    }
    server.createContext(
        path,
        exchange -> {
          exchange
              .getRequestHeaders()
              .forEach(
                  (name, values) -> lastRequestHeaders.put(name.toLowerCase(), values.getFirst()));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", contentType);
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
    return this;
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public String lastRequestHeader(String name) {
    return lastRequestHeaders.get(name.toLowerCase());
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
