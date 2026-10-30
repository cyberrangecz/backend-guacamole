package cz.cyberrange.platform.guacamole.web.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cz.cyberrange.platform.guacamole.StringConstants;
import cz.cyberrange.platform.guacamole.service.GuacamoleTunnelService;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleResourceNotFoundException;
import org.apache.guacamole.io.GuacamoleReader;
import org.apache.guacamole.io.GuacamoleWriter;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.protocol.GuacamoleInstruction;
import org.apache.guacamole.protocol.GuacamoleStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuacamoleWebSocketHandlerTest {

  private static final String BASE_URI = "ws://localhost/websocket/guacamole?";

  @Mock private GuacamoleTunnelService tunnelService;
  @Mock private WebSocketSession session;
  @Mock private GuacamoleTunnel tunnel;
  @Mock private GuacamoleReader reader;

  private final CountDownLatch readerRelease = new CountDownLatch(1);
  private final List<GuacamoleInstruction> writtenInstructions = new ArrayList<>();
  private final Map<String, Object> sessionAttributes = new HashMap<>();
  private GuacamoleWebSocketHandler handler;

  @BeforeEach
  void setUp() throws Exception {
    handler = new GuacamoleWebSocketHandler(tunnelService);
    when(session.getId()).thenReturn("session-1");
    when(session.isOpen()).thenReturn(true);
    when(session.getAttributes()).thenReturn(sessionAttributes);
    sessionAttributes.put(
        StringConstants.AUTHENTICATION_ATTRIBUTE_KEY, new TestingAuthenticationToken("user", "x"));
    when(tunnel.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    when(tunnel.acquireReader()).thenReturn(reader);
    when(reader.read())
        .thenAnswer(
            invocation -> {
              readerRelease.await();
              return null;
            });
    when(tunnel.acquireWriter()).thenReturn(new RecordingWriter());
  }

  @AfterEach
  void tearDown() {
    readerRelease.countDown();
    SecurityContextHolder.clearContext();
  }

  private final class RecordingWriter implements GuacamoleWriter {
    @Override
    public void write(char[] chunk, int offset, int length) {}

    @Override
    public void write(char[] chunk) {}

    @Override
    public void writeInstruction(GuacamoleInstruction instruction) {
      writtenInstructions.add(instruction);
    }
  }

  private void connectWithQuery(String query) throws Exception {
    when(session.getUri()).thenReturn(URI.create(BASE_URI + query));
    handler.afterConnectionEstablished(session);
  }

  private static CloseStatus closeStatusOf(GuacamoleStatus status) {
    return new CloseStatus(
        status.getWebSocketCode(), String.valueOf(status.getGuacamoleStatusCode()));
  }

  private List<String> sentPayloads() throws IOException {
    ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.captor();
    verify(session, org.mockito.Mockito.atLeast(0)).sendMessage(messages.capture());
    return messages.getAllValues().stream()
        .map(message -> message.getPayload().toString())
        .toList();
  }

  @Test
  @DisplayName("getSubProtocols_handler_declaresGuacamoleSubProtocol")
  void getSubProtocols_handler_declaresGuacamoleSubProtocol() {
    assertThat(handler.getSubProtocols()).containsExactly("guacamole");
  }

  @Test
  @DisplayName("supportsPartialMessages_handler_returnsFalse")
  void supportsPartialMessages_handler_returnsFalse() {
    assertThat(handler.supportsPartialMessages()).isFalse();
  }

  @Test
  @DisplayName("afterConnectionEstablished_noAuthenticationAttribute_closesAsUnauthorized")
  void afterConnectionEstablished_noAuthenticationAttribute_closesAsUnauthorized()
      throws Exception {
    sessionAttributes.clear();

    connectWithQuery("sandboxUuid=sb&nodeName=n");

    verify(session).close(closeStatusOf(GuacamoleStatus.CLIENT_UNAUTHORIZED));
    verify(tunnelService, never())
        .createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any());
  }

  @Test
  @DisplayName("afterConnectionEstablished_missingSandboxUuid_throwsGuacamoleException")
  void afterConnectionEstablished_missingSandboxUuid_throwsGuacamoleException() {
    assertThatThrownBy(() -> connectWithQuery("nodeName=n"))
        .isInstanceOf(GuacamoleException.class)
        .hasMessage("Missing required parameter: sandboxUuid");
  }

  @Test
  @DisplayName("afterConnectionEstablished_missingNodeName_throwsGuacamoleException")
  void afterConnectionEstablished_missingNodeName_throwsGuacamoleException() {
    assertThatThrownBy(() -> connectWithQuery("sandboxUuid=sb"))
        .isInstanceOf(GuacamoleException.class)
        .hasMessage("Missing required parameter: nodeName");
  }

  @ParameterizedTest
  @CsvSource({"nodeName=n", "sandboxUuid=sb"})
  @DisplayName("afterConnectionEstablished_missingRequiredParameter_leavesNoSessionLockBehind")
  void afterConnectionEstablished_missingRequiredParameter_leavesNoSessionLockBehind(String query) {
    assertThatThrownBy(() -> connectWithQuery(query)).isInstanceOf(GuacamoleException.class);

    Map<?, ?> sessionLocks = (Map<?, ?>) ReflectionTestUtils.getField(handler, "sessionLocks");
    assertThat(sessionLocks).isEmpty();
  }

  @Test
  @DisplayName("afterConnectionEstablished_noQueryString_throwsGuacamoleException")
  void afterConnectionEstablished_noQueryString_throwsGuacamoleException() {
    when(session.getUri()).thenReturn(URI.create("ws://localhost/websocket/guacamole"));

    assertThatThrownBy(() -> handler.afterConnectionEstablished(session))
        .isInstanceOf(GuacamoleException.class);
  }

  @Test
  @DisplayName("afterConnectionEstablished_validParameters_requestsTunnelWithParsedValues")
  void afterConnectionEstablished_validParameters_requestsTunnelWithParsedValues()
      throws Exception {
    when(tunnelService.createGuacamoleTunnel("sb 1", "node", true, 800, 600)).thenReturn(tunnel);

    connectWithQuery("sandboxUuid=sb%201&nodeName=node&withGui=true&width=800&height=600");

    verify(tunnelService).createGuacamoleTunnel("sb 1", "node", true, 800, 600);
    assertThat(sessionAttributes).containsEntry("guacamole.tunnel", tunnel);
  }

  @ParameterizedTest
  @CsvSource({"true,true", "TRUE,true", "false,false", "yes,false", "'',false"})
  @DisplayName("afterConnectionEstablished_withGuiValue_isGuiOnlyForTrueIgnoringCase")
  void afterConnectionEstablished_withGuiValue_isGuiOnlyForTrueIgnoringCase(
      String withGui, boolean expected) throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);

    connectWithQuery("sandboxUuid=sb&nodeName=n&withGui=" + withGui);

    verify(tunnelService).createGuacamoleTunnel("sb", "n", expected, null, null);
  }

  @Test
  @DisplayName("afterConnectionEstablished_withoutWithGui_requestsTextTunnel")
  void afterConnectionEstablished_withoutWithGui_requestsTextTunnel() throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);

    connectWithQuery("sandboxUuid=sb&nodeName=n");

    verify(tunnelService).createGuacamoleTunnel("sb", "n", false, null, null);
  }

  @Test
  @DisplayName("afterConnectionEstablished_nonNumericWidth_requestsTunnelWithoutWidth")
  void afterConnectionEstablished_nonNumericWidth_requestsTunnelWithoutWidth() throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);

    connectWithQuery("sandboxUuid=sb&nodeName=n&width=wide&height=600");

    verify(tunnelService).createGuacamoleTunnel("sb", "n", false, null, null);
  }

  @Test
  @DisplayName("afterConnectionEstablished_tunnelServiceReturnsNull_closesAsResourceNotFound")
  void afterConnectionEstablished_tunnelServiceReturnsNull_closesAsResourceNotFound()
      throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(null);

    connectWithQuery("sandboxUuid=sb&nodeName=n");

    verify(session).close(closeStatusOf(GuacamoleStatus.RESOURCE_NOT_FOUND));
  }

  @Test
  @DisplayName(
      "afterConnectionEstablished_tunnelServiceThrowsGuacamoleException_closesWithItsStatus")
  void afterConnectionEstablished_tunnelServiceThrowsGuacamoleException_closesWithItsStatus()
      throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenThrow(new GuacamoleResourceNotFoundException("no protocol"));

    connectWithQuery("sandboxUuid=sb&nodeName=n");

    verify(session).close(closeStatusOf(GuacamoleStatus.RESOURCE_NOT_FOUND));
  }

  @Test
  @DisplayName("afterConnectionEstablished_tunnelServiceThrowsRuntimeException_closesAsServerError")
  void afterConnectionEstablished_tunnelServiceThrowsRuntimeException_closesAsServerError()
      throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenThrow(new IllegalStateException("sandbox service down"));

    connectWithQuery("sandboxUuid=sb&nodeName=n");

    verify(session).close(closeStatusOf(GuacamoleStatus.SERVER_ERROR));
  }

  @Test
  @DisplayName("afterConnectionEstablished_tunnelCreated_sendsTunnelUuidAsFirstInstruction")
  void afterConnectionEstablished_tunnelCreated_sendsTunnelUuidAsFirstInstruction()
      throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);

    connectWithQuery("sandboxUuid=sb&nodeName=n");

    verify(session, timeout(2000)).sendMessage(any());
    assertThat(sentPayloads()).first().isEqualTo("0.,36.00000000-0000-0000-0000-000000000001;");
  }

  @Test
  @DisplayName("afterConnectionEstablished_guacdStreamEnds_closesSessionNormally")
  void afterConnectionEstablished_guacdStreamEnds_closesSessionNormally() throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);
    readerReleaseNow();

    connectWithQuery("sandboxUuid=sb&nodeName=n");

    verify(session, timeout(2000)).close(closeStatusOf(GuacamoleStatus.SUCCESS));
  }

  private void readerReleaseNow() {
    readerRelease.countDown();
  }

  @Test
  @DisplayName("handleTextMessage_pingInstruction_answersWithPingEchoAndDoesNotForwardIt")
  void handleTextMessage_pingInstruction_answersWithPingEchoAndDoesNotForwardIt() throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);
    connectWithQuery("sandboxUuid=sb&nodeName=n");

    handler.handleMessage(session, new TextMessage("0.,4.ping,1.7;"));

    assertThat(sentPayloads()).contains("0.,4.ping,1.7;");
    assertThat(writtenInstructions).isEmpty();
    verify(tunnel).releaseWriter();
  }

  @Test
  @DisplayName("handleTextMessage_regularInstruction_forwardsItToGuacd")
  void handleTextMessage_regularInstruction_forwardsItToGuacd() throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);
    connectWithQuery("sandboxUuid=sb&nodeName=n");

    handler.handleMessage(session, new TextMessage("5.mouse,1.1,1.2,1.0;"));

    assertThat(writtenInstructions)
        .singleElement()
        .satisfies(instruction -> assertThat(instruction.getOpcode()).isEqualTo("mouse"));
    verify(tunnel).releaseWriter();
  }

  @Test
  @DisplayName("handleTextMessage_sessionWithoutTunnel_ignoresMessage")
  void handleTextMessage_sessionWithoutTunnel_ignoresMessage() throws Exception {
    handler.handleMessage(session, new TextMessage("5.mouse,1.1,1.2,1.0;"));

    verify(tunnel, never()).acquireWriter();
    assertThat(writtenInstructions).isEmpty();
  }

  @Test
  @DisplayName("afterConnectionClosed_sessionWithTunnel_closesTunnel")
  void afterConnectionClosed_sessionWithTunnel_closesTunnel() throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);
    connectWithQuery("sandboxUuid=sb&nodeName=n");

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(tunnel).close();
  }

  @Test
  @DisplayName("afterConnectionClosed_tunnelFailsToClose_doesNotPropagateFailure")
  void afterConnectionClosed_tunnelFailsToClose_doesNotPropagateFailure() throws Exception {
    when(tunnelService.createGuacamoleTunnel(anyString(), anyString(), anyBoolean(), any(), any()))
        .thenReturn(tunnel);
    org.mockito.Mockito.doThrow(new GuacamoleException("close failed")).when(tunnel).close();
    connectWithQuery("sandboxUuid=sb&nodeName=n");

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(tunnel).close();
  }

  @Test
  @DisplayName("afterConnectionClosed_unknownSession_completesWithoutError")
  void afterConnectionClosed_unknownSession_completesWithoutError() throws Exception {
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(session, never()).close();
  }

  @Test
  @DisplayName("handleTransportError_anyError_closesSessionAsServerError")
  void handleTransportError_anyError_closesSessionAsServerError() throws Exception {
    handler.handleTransportError(session, new IOException("broken pipe"));

    verify(session).close(closeStatusOf(GuacamoleStatus.SERVER_ERROR));
  }

  @Test
  @DisplayName("handleTransportError_alreadyClosedSession_doesNotCloseAgain")
  void handleTransportError_alreadyClosedSession_doesNotCloseAgain() throws Exception {
    when(session.isOpen()).thenReturn(false);

    handler.handleTransportError(session, new IOException("broken pipe"));

    verify(session, never()).close(any(CloseStatus.class));
  }
}
