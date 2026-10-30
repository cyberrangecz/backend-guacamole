package cz.cyberrange.platform.guacamole.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cz.cyberrange.platform.guacamole.config.GuacamoleProtocolOverrides;
import cz.cyberrange.platform.guacamole.model.GuacamoleProtocol;
import cz.cyberrange.platform.guacamole.model.dto.ProtocolDto;
import cz.cyberrange.platform.guacamole.model.dto.VmConnectionDataDto;
import cz.cyberrange.platform.guacamole.support.FakeGuacd;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleResourceNotFoundException;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuacamoleTunnelServiceTest {

  private static final String SANDBOX_ID = "sandbox-1";
  private static final String NODE_NAME = "node-a";

  @Mock private SandboxCommunicationService sandboxCommunicationService;
  @Mock private GuacamoleProtocolOverrides protocolOverrides;

  private GuacamoleTunnelService tunnelService;
  private FakeGuacd fakeGuacd;

  @BeforeEach
  void setUp() throws IOException {
    tunnelService = new GuacamoleTunnelService(sandboxCommunicationService, protocolOverrides);
    fakeGuacd = new FakeGuacd(true);
  }

  @AfterEach
  void tearDown() throws IOException {
    fakeGuacd.close();
  }

  private static ProtocolDto protocol(GuacamoleProtocol name, Integer port) {
    ProtocolDto dto = new ProtocolDto();
    dto.setName(name);
    dto.setPort(port);
    return dto;
  }

  private VmConnectionDataDto connectionData(int guacdPort, ProtocolDto... protocols) {
    VmConnectionDataDto data = new VmConnectionDataDto();
    data.setManIp("127.0.0.1");
    data.setManPort(guacdPort);
    data.setHostIp("10.1.2.3");
    data.setProtocols(List.of(protocols));
    return data;
  }

  private void stubConnectionData(ProtocolDto... protocols) {
    when(sandboxCommunicationService.getConnectionData(SANDBOX_ID, NODE_NAME))
        .thenReturn(connectionData(fakeGuacd.getPort(), protocols));
  }

  @Test
  @DisplayName("createGuacamoleTunnel_textOnlyRequest_connectsSshWithHostAndPort")
  void createGuacamoleTunnel_textOnlyRequest_connectsSshWithHostAndPort() throws Exception {
    stubConnectionData(protocol(GuacamoleProtocol.RDP, 3389), protocol(GuacamoleProtocol.SSH, 22));
    when(protocolOverrides.forProtocol(GuacamoleProtocol.SSH)).thenReturn(Map.of());

    GuacamoleTunnel tunnel =
        tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, 800, 600);

    assertThat(tunnel).isNotNull();
    assertThat(fakeGuacd.getReceivedInstructions().getFirst().getArgs()).containsExactly("ssh");
    assertThat(fakeGuacd.awaitConnectArguments())
        .containsEntry("hostname", "10.1.2.3")
        .containsEntry("port", "22")
        .containsEntry("width", "")
        .containsEntry("resize-method", "");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_graphicalRequest_connectsRdpWithDisplayOptions")
  void createGuacamoleTunnel_graphicalRequest_connectsRdpWithDisplayOptions() throws Exception {
    stubConnectionData(protocol(GuacamoleProtocol.SSH, 22), protocol(GuacamoleProtocol.RDP, 3389));
    when(protocolOverrides.forProtocol(GuacamoleProtocol.RDP)).thenReturn(Map.of());

    tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, true, 1280, 720);

    assertThat(fakeGuacd.getReceivedInstructions().getFirst().getArgs()).containsExactly("rdp");
    assertThat(fakeGuacd.awaitConnectArguments())
        .containsEntry("port", "3389")
        .containsEntry("width", "1280")
        .containsEntry("height", "720")
        .containsEntry("resize-method", "display-update");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_rdpWithoutDimensions_omitsWidthAndHeight")
  void createGuacamoleTunnel_rdpWithoutDimensions_omitsWidthAndHeight() throws Exception {
    stubConnectionData(protocol(GuacamoleProtocol.RDP, 3389));
    when(protocolOverrides.forProtocol(GuacamoleProtocol.RDP)).thenReturn(Map.of());

    tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, true, null, null);

    assertThat(fakeGuacd.awaitConnectArguments())
        .containsEntry("width", "")
        .containsEntry("height", "")
        .containsEntry("resize-method", "display-update");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_vncRequest_ignoresDisplayDimensions")
  void createGuacamoleTunnel_vncRequest_ignoresDisplayDimensions() throws Exception {
    stubConnectionData(protocol(GuacamoleProtocol.VNC, 5900));
    when(protocolOverrides.forProtocol(GuacamoleProtocol.VNC)).thenReturn(Map.of());

    tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, true, 1280, 720);

    assertThat(fakeGuacd.awaitConnectArguments())
        .containsEntry("port", "5900")
        .containsEntry("width", "")
        .containsEntry("height", "");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_overridesDefined_overridesReplaceDefaultParameters")
  void createGuacamoleTunnel_overridesDefined_overridesReplaceDefaultParameters() throws Exception {
    stubConnectionData(protocol(GuacamoleProtocol.RDP, 3389));
    when(protocolOverrides.forProtocol(GuacamoleProtocol.RDP))
        .thenReturn(Map.of("disable-bitmap-caching", "true", "resize-method", "reconnect"));

    tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, true, 800, 600);

    assertThat(fakeGuacd.awaitConnectArguments())
        .containsEntry("disable-bitmap-caching", "true")
        .containsEntry("resize-method", "reconnect");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_noProtocolMatchesRequestedMode_throwsResourceNotFound")
  void createGuacamoleTunnel_noProtocolMatchesRequestedMode_throwsResourceNotFound() {
    stubConnectionData(protocol(GuacamoleProtocol.SSH, 22));

    assertThatThrownBy(
            () -> tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, true, null, null))
        .isInstanceOf(GuacamoleResourceNotFoundException.class)
        .hasMessage("No protocol with GUI found for node 'node-a'");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_onlyGraphicalProtocolsForTextRequest_throwsResourceNotFound")
  void createGuacamoleTunnel_onlyGraphicalProtocolsForTextRequest_throwsResourceNotFound() {
    stubConnectionData(protocol(GuacamoleProtocol.RDP, 3389));

    assertThatThrownBy(
            () -> tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, null, null))
        .isInstanceOf(GuacamoleResourceNotFoundException.class)
        .hasMessage("No protocol without GUI found for node 'node-a'");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_nodeWithoutProtocols_throwsResourceNotFound")
  void createGuacamoleTunnel_nodeWithoutProtocols_throwsResourceNotFound() {
    stubConnectionData();

    assertThatThrownBy(
            () -> tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, null, null))
        .isInstanceOf(GuacamoleResourceNotFoundException.class);
  }

  @Test
  @DisplayName("createGuacamoleTunnel_protocolWithoutName_isSkippedDuringSelection")
  void createGuacamoleTunnel_protocolWithoutName_isSkippedDuringSelection() throws Exception {
    stubConnectionData(protocol(null, 1), protocol(GuacamoleProtocol.SSH, 22));
    when(protocolOverrides.forProtocol(GuacamoleProtocol.SSH)).thenReturn(Map.of());

    tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, null, null);

    assertThat(fakeGuacd.awaitConnectArguments()).containsEntry("port", "22");
  }

  @Test
  @DisplayName("createGuacamoleTunnel_guacdRejectsHandshake_throwsGuacamoleException")
  void createGuacamoleTunnel_guacdRejectsHandshake_throwsGuacamoleException() throws Exception {
    try (FakeGuacd rejectingGuacd = new FakeGuacd(false)) {
      when(sandboxCommunicationService.getConnectionData(SANDBOX_ID, NODE_NAME))
          .thenReturn(
              connectionData(rejectingGuacd.getPort(), protocol(GuacamoleProtocol.SSH, 22)));
      when(protocolOverrides.forProtocol(GuacamoleProtocol.SSH)).thenReturn(Map.of());

      assertThatThrownBy(
              () -> tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, null, null))
          .isInstanceOf(GuacamoleException.class);
    }
  }

  @Test
  @DisplayName("createGuacamoleTunnel_guacdUnreachable_throwsGuacamoleException")
  void createGuacamoleTunnel_guacdUnreachable_throwsGuacamoleException() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort();
    }
    when(sandboxCommunicationService.getConnectionData(SANDBOX_ID, NODE_NAME))
        .thenReturn(connectionData(closedPort, protocol(GuacamoleProtocol.SSH, 22)));
    when(protocolOverrides.forProtocol(GuacamoleProtocol.SSH)).thenReturn(Map.of());

    assertThatThrownBy(
            () -> tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, null, null))
        .isInstanceOf(GuacamoleException.class);
  }

  @Test
  @DisplayName("createGuacamoleTunnel_noConnectionDataReturned_throwsGuacamoleException")
  void createGuacamoleTunnel_noConnectionDataReturned_throwsGuacamoleException() {
    when(sandboxCommunicationService.getConnectionData(SANDBOX_ID, NODE_NAME)).thenReturn(null);

    assertThatThrownBy(
            () -> tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, null, null))
        .isInstanceOf(GuacamoleResourceNotFoundException.class);
  }

  @Test
  @DisplayName("createGuacamoleTunnel_protocolWithoutPort_throwsGuacamoleException")
  void createGuacamoleTunnel_protocolWithoutPort_throwsGuacamoleException() {
    stubConnectionData(protocol(GuacamoleProtocol.SSH, null));

    assertThatThrownBy(
            () -> tunnelService.createGuacamoleTunnel(SANDBOX_ID, NODE_NAME, false, null, null))
        .isInstanceOf(GuacamoleException.class);
  }
}
