package cz.cyberrange.platform.guacamole.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class TextWebsocketErrorNotifierTest {

  @Mock private WebSocketSession session;

  @Test
  @DisplayName("closeWithMessage_openSession_sendsErrorTextAndClosesWithStatus")
  void closeWithMessage_openSession_sendsErrorTextAndClosesWithStatus() throws IOException {
    when(session.isOpen()).thenReturn(true);

    TextWebsocketErrorNotifier.closeWithMessage(session, "bad node", CloseStatus.SERVER_ERROR);

    ArgumentCaptor<WebSocketMessage<?>> message = ArgumentCaptor.captor();
    verify(session).sendMessage(message.capture());
    assertThat(message.getValue()).isInstanceOf(TextMessage.class);
    assertThat(message.getValue().getPayload()).isEqualTo("Error encountered: bad node");
    verify(session).close(CloseStatus.SERVER_ERROR);
  }

  @Test
  @DisplayName("closeWithMessage_closedSession_sendsAndClosesNothing")
  void closeWithMessage_closedSession_sendsAndClosesNothing() throws IOException {
    when(session.isOpen()).thenReturn(false);

    TextWebsocketErrorNotifier.closeWithMessage(session, "late", CloseStatus.NORMAL);

    verify(session, never()).sendMessage(any());
    verify(session, never()).close(any(CloseStatus.class));
  }
}
