package cz.cyberrange.platform.guacamole.web.websocket;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
public class TextWebsocketErrorNotifier {
  private static String buildCloseConnectionMessage(String message) {
    return "Closing connection: %s".formatted(message);
  }

  public static void closeWithMessage(WebSocketSession session, String message, CloseStatus status)
      throws IOException {
    if (session.isOpen()) {
      session.sendMessage(
          new org.springframework.web.socket.TextMessage("Error encountered: " + message));
      session.close(status);
    }
    if (status.equals(CloseStatus.NORMAL)) {
      log.info(buildCloseConnectionMessage(message));
    } else {
      log.error(buildCloseConnectionMessage(message));
    }
  }
}
