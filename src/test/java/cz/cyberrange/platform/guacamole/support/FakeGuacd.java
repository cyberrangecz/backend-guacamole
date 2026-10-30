package cz.cyberrange.platform.guacamole.support;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.io.ReaderGuacamoleReader;
import org.apache.guacamole.protocol.GuacamoleInstruction;

/** In-process stand-in for guacd that answers the Guacamole handshake on a loopback port. */
public final class FakeGuacd implements AutoCloseable {

  private static final List<String> ADVERTISED_ARGUMENTS =
      List.of("hostname", "port", "width", "height", "resize-method", "disable-bitmap-caching");

  private final ServerSocket serverSocket;
  private final boolean acceptHandshake;
  private final CountDownLatch handshakeFinished = new CountDownLatch(1);
  private final List<GuacamoleInstruction> receivedInstructions =
      Collections.synchronizedList(new ArrayList<>());
  private volatile Map<String, String> connectArguments = Map.of();

  public FakeGuacd(boolean acceptHandshake) throws IOException {
    this.acceptHandshake = acceptHandshake;
    this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    Thread serverThread = new Thread(this::serveOneConnection, "fake-guacd");
    serverThread.setDaemon(true);
    serverThread.start();
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

  /** Waits for the handshake and returns the parameter values sent in the connect instruction. */
  public Map<String, String> awaitConnectArguments() throws InterruptedException {
    if (!handshakeFinished.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Handshake did not finish");
    }
    return connectArguments;
  }

  public List<GuacamoleInstruction> getReceivedInstructions() {
    return List.copyOf(receivedInstructions);
  }

  private void serveOneConnection() {
    try (Socket socket = serverSocket.accept();
        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
      ReaderGuacamoleReader reader =
          new ReaderGuacamoleReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      GuacamoleInstruction instruction = reader.readInstruction();
      receivedInstructions.add(instruction);
      writer.write(
          new GuacamoleInstruction("args", ADVERTISED_ARGUMENTS.toArray(String[]::new)).toString());
      writer.flush();
      while (instruction != null && !"connect".equals(instruction.getOpcode())) {
        instruction = reader.readInstruction();
        receivedInstructions.add(instruction);
      }
      if (instruction != null) {
        recordConnectArguments(instruction);
        String reply =
            acceptHandshake
                ? new GuacamoleInstruction("ready", "fake-connection").toString()
                : new GuacamoleInstruction("error", "refused", "519").toString();
        writer.write(reply);
        writer.flush();
      }
      handshakeFinished.countDown();
      reader.read();
    } catch (IOException | GuacamoleException ignored) {
      handshakeFinished.countDown();
    }
  }

  private void recordConnectArguments(GuacamoleInstruction connect) {
    Map<String, String> arguments = new LinkedHashMap<>();
    List<String> values = connect.getArgs();
    for (int index = 0; index < ADVERTISED_ARGUMENTS.size() && index < values.size(); index++) {
      arguments.put(ADVERTISED_ARGUMENTS.get(index), values.get(index));
    }
    connectArguments = arguments;
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
  }
}
