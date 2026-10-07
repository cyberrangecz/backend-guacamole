package cz.cyberrange.platform.guacamole.service;

import static org.assertj.core.api.Assertions.assertThat;

import cz.cyberrange.platform.guacamole.model.GuacamoleProtocol;
import cz.cyberrange.platform.guacamole.model.dto.VmConnectionDataDto;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SandboxCommunicationServiceTest {

  private static final String BODY =
      "{\"man_ip\":\"10.0.0.1\",\"man_port\":22,\"host_ip\":\"10.0.0.2\","
          + "\"protocols\":[{\"name\":\"ssh\",\"port\":22}]}";

  @Test
  @DisplayName("getConnectionData_sandboxAndNode_requestsTopologyEndpointAndMapsBody")
  void getConnectionData_sandboxAndNode_requestsTopologyEndpointAndMapsBody() {
    AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();
    ExchangeFunction exchange =
        request -> {
          sentRequest.set(request);
          return Mono.just(
              ClientResponse.create(HttpStatus.OK)
                  .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                  .body(BODY)
                  .build());
        };
    SandboxCommunicationService service =
        new SandboxCommunicationService(
            WebClient.builder()
                .baseUrl("http://sandbox.test/api")
                .exchangeFunction(exchange)
                .build());

    VmConnectionDataDto data = service.getConnectionData("sb-1", "node-a");

    assertThat(sentRequest.get().url().toString())
        .isEqualTo("http://sandbox.test/api/sandboxes/sb-1/topology/node-a");
    assertThat(sentRequest.get().method().name()).isEqualTo("GET");
    assertThat(data.getManIp()).isEqualTo("10.0.0.1");
    assertThat(data.getProtocols())
        .singleElement()
        .satisfies(protocol -> assertThat(protocol.getName()).isEqualTo(GuacamoleProtocol.SSH));
  }
}
