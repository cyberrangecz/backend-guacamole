package cz.cyberrange.platform.guacamole.service;

import cz.cyberrange.platform.guacamole.model.dto.VmConnectionDataDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/** Service for HTTP communication with Sandbox service. */
@Service
public class SandboxCommunicationService {

  private static final String SANDBOX_API = "/sandboxes/%s/topology/%s";

  private final WebClient sandboxServiceWebClient;

  /**
   * Instantiates a new Sandbox communication service.
   *
   * @param sandboxWebClient client for HTTP communication
   */
  public SandboxCommunicationService(
      @Qualifier("sandboxServiceWebClient") WebClient sandboxWebClient) {
    this.sandboxServiceWebClient = sandboxWebClient;
  }

  private static String getSadndboxNodeEndpoint(String sandboxId, String nodeName) {
    return String.format(SANDBOX_API, sandboxId, nodeName);
  }

  /**
   * Gets connection data of the specified node inside sandbox.
   *
   * @param sandboxId the sandbox id
   * @param nodeName the node name
   * @return the connection data
   */
  public VmConnectionDataDto getConnectionData(String sandboxId, String nodeName) {
    return sandboxServiceWebClient
        .get()
        .uri(builder -> builder.path(getSadndboxNodeEndpoint(sandboxId, nodeName)).build())
        .retrieve()
        .bodyToMono(VmConnectionDataDto.class)
        .block();
  }
}
