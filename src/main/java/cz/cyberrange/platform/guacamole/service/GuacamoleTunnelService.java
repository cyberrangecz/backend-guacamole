package cz.cyberrange.platform.guacamole.service;

import cz.cyberrange.platform.guacamole.model.dto.ProtocolDto;
import cz.cyberrange.platform.guacamole.model.dto.VmConnectionDataDto;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleSocket;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.net.SimpleGuacamoleTunnel;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** This service contains utilities for the management of Guacamole tunnels. */
@Slf4j
@Service
public class GuacamoleTunnelService {

  private final SandboxCommunicationService sandboxCommunicationService;

  /**
   * Instantiates a new Guacamole service.
   *
   * @param sandboxService service for API calls to sandbox microservice
   */
  @Autowired
  public GuacamoleTunnelService(SandboxCommunicationService sandboxService) {
    this.sandboxCommunicationService = sandboxService;
  }

  private static Optional<ProtocolDto> findProtocol(
      Collection<ProtocolDto> protocols, boolean isGui) {
    if (protocols == null || protocols.isEmpty()) {
      log.warn("No protocols available");
      return Optional.empty();
    }
    return protocols.stream()
        .filter(protocol -> isGui != "SSH".equalsIgnoreCase(protocol.getName()))
        .peek(protocol -> log.info("Found protocol {}", protocol.getName()))
        .findFirst();
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

  /**
   * Creates a GuacamoleTunnel for the specified sandbox and node.
   *
   * @param sandboxId id of parent sandbox
   * @param nodeName name of sandbox node
   * @param isGui whether to use GUI enabled protocol
   * @param width what display width to apply (only for compatible protocols)
   * @param height what display height to apply (only for compatible protocols)
   * @return GuacamoleTunnel
   * @throws GuacamoleException on failure to create tunnel
   * @throws IllegalStateException when no suitable protocol is found
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
      throw new IllegalStateException(
          "No protocol %s found for node '%s'"
              .formatted(isGui ? "with GUI" : "without GUI", nodeName));
    }

    String protocolName = protocol.get().getName().toLowerCase();

    GuacamoleConfiguration config = new GuacamoleConfiguration();
    config.setProtocol(protocolName);
    config.setParameter("hostname", data.getHostIp());
    config.setParameter("port", protocol.get().getPort().toString());

    if (protocolName.equalsIgnoreCase("rdp")) {
      configureRdpOptions(config, width, height);
    }

    GuacamoleSocket socket =
        new ConfiguredGuacamoleSocket(
            new InetGuacamoleSocket(data.getManIp(), data.getManPort()), config);

    return new SimpleGuacamoleTunnel(socket);
  }
}
