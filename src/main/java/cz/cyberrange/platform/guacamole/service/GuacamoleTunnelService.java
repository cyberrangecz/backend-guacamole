package cz.cyberrange.platform.guacamole.service;

import cz.cyberrange.platform.guacamole.config.GuacamoleProtocolOverrides;
import cz.cyberrange.platform.guacamole.model.GuacamoleProtocol;
import cz.cyberrange.platform.guacamole.model.dto.ProtocolDto;
import cz.cyberrange.platform.guacamole.model.dto.VmConnectionDataDto;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleResourceNotFoundException;
import org.apache.guacamole.net.GuacamoleSocket;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.net.SimpleGuacamoleTunnel;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** This service contains utilities for the management of Guacamole tunnels. */
@Slf4j
@Service
public class GuacamoleTunnelService {

  private final SandboxCommunicationService sandboxCommunicationService;
  private final GuacamoleProtocolOverrides protocolOverrides;

  /**
   * Instantiates a new Guacamole service.
   *
   * @param sandboxService service for API calls to sandbox microservice
   * @param protocolOverrides holder of user-defined per-protocol connection parameter overrides
   */
  public GuacamoleTunnelService(
      SandboxCommunicationService sandboxService, GuacamoleProtocolOverrides protocolOverrides) {
    this.sandboxCommunicationService = sandboxService;
    this.protocolOverrides = protocolOverrides;
  }

  private static Optional<ProtocolDto> findProtocol(
      Collection<ProtocolDto> protocols, boolean isGui) {
    if (protocols == null || protocols.isEmpty()) {
      log.warn("No protocols available");
      return Optional.empty();
    }
    Optional<ProtocolDto> selected =
        protocols.stream()
            .filter(
                protocol -> protocol.getName() != null && protocol.getName().isGraphical() == isGui)
            .findFirst();
    selected.ifPresent(protocol -> log.info("Found protocol {}", protocol.getName()));
    return selected;
  }

  private static void configureRdpOptions(
      GuacamoleConfiguration config, @Nullable Integer width, @Nullable Integer height) {
    if (width != null) {
      config.setParameter("width", String.valueOf(width));
    }
    if (height != null) {
      config.setParameter("height", String.valueOf(height));
    }
    config.setParameter("resize-method", "display-update");
  }

  private static void closeAndSuppressFailure(GuacamoleSocket socket, Throwable primaryFailure) {
    try {
      socket.close();
    } catch (GuacamoleException | RuntimeException closeFailure) {
      primaryFailure.addSuppressed(closeFailure);
    }
  }

  private void applyProtocolOverrides(GuacamoleConfiguration config, GuacamoleProtocol protocol) {
    this.protocolOverrides.forProtocol(protocol).forEach(config::setParameter);
  }

  /**
   * Creates a GuacamoleTunnel for the specified sandbox and node.
   *
   * @param sandboxId id of parent sandbox
   * @param nodeName name of sandbox node
   * @param isGui whether to use GUI enabled protocol
   * @param width what display width to apply (only for compatible protocols)
   * @param height what display height to apply (only for compatible protocols)
   * @return GuacamoleTunnel
   * @throws GuacamoleException on failure to create tunnel, or when no suitable protocol is found
   */
  public GuacamoleTunnel createGuacamoleTunnel(
      @NonNull String sandboxId,
      @NonNull String nodeName,
      boolean isGui,
      @Nullable Integer width,
      @Nullable Integer height)
      throws GuacamoleException {

    VmConnectionDataDto data = sandboxCommunicationService.getConnectionData(sandboxId, nodeName);

    Optional<ProtocolDto> protocol = findProtocol(data.getProtocols(), isGui);

    if (protocol.isEmpty()) {
      throw new GuacamoleResourceNotFoundException(
          "No protocol %s found for node '%s'"
              .formatted(isGui ? "with GUI" : "without GUI", nodeName));
    }

    ProtocolDto selectedProtocol = protocol.get();
    GuacamoleProtocol protocolType = selectedProtocol.getName();

    GuacamoleConfiguration config = new GuacamoleConfiguration();
    config.setProtocol(protocolType.getProtocolName());
    config.setParameter("hostname", data.getHostIp());
    config.setParameter("port", selectedProtocol.getPort().toString());

    if (protocolType == GuacamoleProtocol.RDP) {
      configureRdpOptions(config, width, height);
    }

    this.applyProtocolOverrides(config, protocolType);

    GuacamoleSocket guacdSocket = new InetGuacamoleSocket(data.getManIp(), data.getManPort());
    try {
      return new SimpleGuacamoleTunnel(new ConfiguredGuacamoleSocket(guacdSocket, config));
    } catch (GuacamoleException | RuntimeException handshakeFailure) {
      closeAndSuppressFailure(guacdSocket, handshakeFailure);
      throw handshakeFailure;
    }
  }
}
