package cz.cyberrange.platform.guacamole.web.websocket;

import cz.cyberrange.platform.guacamole.StringConstants;
import cz.cyberrange.platform.guacamole.service.GuacamoleTunnelService;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleConnectionClosedException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.io.GuacamoleReader;
import org.apache.guacamole.io.GuacamoleWriter;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.protocol.FilteredGuacamoleWriter;
import org.apache.guacamole.protocol.GuacamoleInstruction;
import org.apache.guacamole.protocol.GuacamoleStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Websocket handler fulfilling the Guacamole protocol */
@Slf4j
public class GuacamoleWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

  private static final int BUFFER_SIZE = 8192;
  private static final String PING_OPCODE = "ping";
  private static final String TUNNEL_ATTRIBUTE = "guacamole.tunnel";
  private static final String PARAM_SANDBOX_UUID = "sandboxUuid";
  private static final String PARAM_NODE_NAME = "nodeName";
  private static final String PARAM_WITH_GUI = "withGui";
  private static final String PARAM_WIDTH = "width";
  private static final String PARAM_HEIGHT = "height";
  private final ConcurrentMap<String, GuacamoleTunnel> tunnels = new ConcurrentHashMap<>();
  private final GuacamoleTunnelService guacamoleTunnelService;

  /** Instantiates a new Guacamole web socket handler. */
  public GuacamoleWebSocketHandler(GuacamoleTunnelService tunnelService) {
    this.guacamoleTunnelService = tunnelService;
  }

  private static String getOrThrow(Map<String, String> map, String key) throws GuacamoleException {
    String value = map.get(key);
    if (value == null) {
      throw new GuacamoleException("Missing required parameter: " + key);
    }
    return value;
  }

  /** Sends a Guacamole instruction to the WebSocket client. */
  private static void sendInstruction(WebSocketSession session, GuacamoleInstruction instruction)
      throws IOException {
    sendInstruction(session, instruction.toString());
  }

  /** Sends a Guacamole instruction string to the WebSocket client. */
  private static void sendInstruction(WebSocketSession session, String instruction)
      throws IOException {
    if (session.isOpen()) {
      session.sendMessage(new TextMessage(instruction));
    }
  }

  /** Closes the WebSocket connection with the given Guacamole status. */
  private static void closeConnection(WebSocketSession session, GuacamoleStatus guacStatus) {
    closeConnection(session, guacStatus.getGuacamoleStatusCode(), guacStatus.getWebSocketCode());
  }

  /** Closes the WebSocket connection with the given status codes. */
  private static void closeConnection(
      WebSocketSession session, int guacamoleStatusCode, int webSocketCode) {

    try {
      if (session.isOpen()) {
        CloseStatus closeStatus =
            new CloseStatus(webSocketCode, String.valueOf(guacamoleStatusCode));
        session.close(closeStatus);
      }
    } catch (IOException e) {
      log.debug("Unable to close WebSocket connection.", e);
    }
  }

  /**
   * Starts a thread that continuously reads from the Guacamole tunnel and sends instructions to the
   * WebSocket client.
   */
  private static void startReadThread(WebSocketSession session, GuacamoleTunnel tunnel) {

    Thread readThread =
        new Thread(
            () -> {
              StringBuilder buffer = new StringBuilder(BUFFER_SIZE);
              GuacamoleReader reader = tunnel.acquireReader();
              char[] readMessage;

              try {
                // Send tunnel UUID as first instruction
                sendInstruction(
                    session,
                    new GuacamoleInstruction(
                        GuacamoleTunnel.INTERNAL_DATA_OPCODE, tunnel.getUUID().toString()));

                // Continuously read and forward instructions
                while (session.isOpen() && (readMessage = reader.read()) != null) {
                  buffer.append(readMessage);
                  if (!reader.available() || buffer.length() >= BUFFER_SIZE) {
                    sendInstruction(session, buffer.toString());
                    buffer.setLength(0);
                  }
                }

                // No more data - close normally
                closeConnection(session, GuacamoleStatus.SUCCESS);

              } catch (GuacamoleClientException e) {
                log.info("WebSocket connection terminated: {}", e.getMessage());
                log.debug("WebSocket connection terminated due to client error.", e);
                closeConnection(session, e.getStatus());

              } catch (GuacamoleConnectionClosedException e) {
                log.debug("Connection to guacd closed.", e);
                closeConnection(session, GuacamoleStatus.SUCCESS);

              } catch (GuacamoleException e) {
                log.error("Connection to guacd terminated abnormally: {}", e.getMessage());
                log.debug("Internal error during connection to guacd.", e);
                closeConnection(session, e.getStatus());

              } catch (IOException e) {
                log.debug("I/O error prevents further reads.", e);
                GuacamoleWebSocketHandler.closeConnection(session, GuacamoleStatus.SERVER_ERROR);
              }
            },
            "guacamole-reader-" + session.getId());

    readThread.setDaemon(true);
    readThread.start();
  }

  private static Map<String, String> extractParams(URI uri) {
    Map<String, String> queryPairs = new HashMap<>();
    if (uri == null) return queryPairs;

    String query = uri.getQuery();
    if (query == null) return queryPairs;

    Arrays.stream(query.split("&"))
        .forEach(
            pair -> {
              String[] parts = pair.split("=");
              if (parts.length == 2) {
                queryPairs.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
              }
            });

    return queryPairs;
  }

  @Override
  public void afterConnectionEstablished(@NonNull WebSocketSession session)
      throws GuacamoleException {

    // Get authentication from session attributes (set by interceptor)
    Authentication authentication =
        (Authentication) session.getAttributes().get(StringConstants.AUTHENTICATION_ATTRIBUTE_KEY);

    if (authentication == null) {
      log.error("No authentication found in session attributes");
      GuacamoleWebSocketHandler.closeConnection(session, GuacamoleStatus.CLIENT_UNAUTHORIZED);
      return;
    }

    SecurityContextHolder.getContext().setAuthentication(authentication);

    Map<String, String> params = GuacamoleWebSocketHandler.extractParams(session.getUri());

    String sandboxId = getOrThrow(params, PARAM_SANDBOX_UUID);

    Integer width = null;
    Integer height = null;
    try {
      if (params.containsKey(PARAM_WIDTH)) {
        width = Integer.parseInt(params.get(PARAM_WIDTH));
      }
      if (params.containsKey(PARAM_HEIGHT)) {
        height = Integer.parseInt(params.get(PARAM_HEIGHT));
      }
    } catch (NumberFormatException e) {
      log.warn("Invalid width or height parameter: {}", e.getMessage());
    }

    String nodeName = getOrThrow(params, PARAM_NODE_NAME);
    boolean withGui = params.getOrDefault(PARAM_WITH_GUI, "false").equalsIgnoreCase("true");
    log.info(
        "Creating Guacamole tunnel - User: {}, Sandbox: {}, Node: {}",
        authentication.getName(),
        sandboxId,
        nodeName);
    try {
      GuacamoleTunnel tunnel =
          guacamoleTunnelService.createGuacamoleTunnel(sandboxId, nodeName, withGui, width, height);

      if (tunnel == null) {
        log.error("Failed to create tunnel - returned null");
        closeConnection(session, GuacamoleStatus.RESOURCE_NOT_FOUND);
        return;
      }

      tunnels.put(session.getId(), tunnel);
      session.getAttributes().put(TUNNEL_ATTRIBUTE, tunnel);

      startReadThread(session, tunnel);
    } catch (GuacamoleException e) {
      log.error("Creation of WebSocket tunnel to guacd failed: {}", e.getMessage());
      log.debug("Error connecting WebSocket tunnel.", e);
      closeConnection(session, e.getStatus());
    }
  }

  @Override
  protected void handleTextMessage(
      @NonNull WebSocketSession session, @NonNull TextMessage message) {

    GuacamoleTunnel tunnel = tunnels.get(session.getId());

    if (tunnel == null) {
      log.warn("Received message for session with no tunnel: {}", session.getId());
      return;
    }

    // Filter received instructions, handling tunnel-internal instructions
    // without passing through to guacd
    GuacamoleWriter writer =
        new FilteredGuacamoleWriter(
            tunnel.acquireWriter(),
            instruction -> {

              // Filter out all tunnel-internal instructions
              if (instruction.getOpcode().equals(GuacamoleTunnel.INTERNAL_DATA_OPCODE)) {

                // Respond to ping requests
                List<String> args = instruction.getArgs();
                if (args.size() >= 2 && args.get(0).equals(PING_OPCODE)) {

                  try {
                    sendInstruction(
                        session,
                        new GuacamoleInstruction(
                            GuacamoleTunnel.INTERNAL_DATA_OPCODE, PING_OPCODE, args.get(1)));
                  } catch (IOException e) {
                    log.debug("Unable to send \"ping\" response for WebSocket tunnel.", e);
                  }
                }

                return null;
              }

              // Pass through all non-internal instructions untouched
              return instruction;
            });

    try {
      // Write received message
      writer.write(message.getPayload().toCharArray());

    } catch (GuacamoleConnectionClosedException e) {
      log.debug("Connection to guacd closed.", e);

    } catch (GuacamoleException e) {
      log.debug("WebSocket tunnel write failed.", e);
    }

    tunnel.releaseWriter();
  }

  @Override
  public void afterConnectionClosed(
      @NonNull WebSocketSession session, @NonNull CloseStatus status) {

    log.info("WebSocket closed - Session: {}, Status: {}", session.getId(), status);

    GuacamoleTunnel tunnel = tunnels.remove(session.getId());

    if (tunnel != null) {
      try {
        tunnel.close();
      } catch (GuacamoleException e) {
        log.debug("Unable to close WebSocket tunnel.", e);
      }
    }
  }

  @Override
  public void handleTransportError(
      @NonNull WebSocketSession session, @NonNull Throwable exception) {

    log.error("WebSocket transport error - Session: {}", session.getId(), exception);
    GuacamoleWebSocketHandler.closeConnection(session, GuacamoleStatus.SERVER_ERROR);
  }

  @Override
  public boolean supportsPartialMessages() {
    return false;
  }

  @Override
  public List<String> getSubProtocols() {
    return List.of("guacamole");
  }
}
