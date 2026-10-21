package cz.cyberrange.platform.guacamole.service;

import static org.assertj.core.api.Assertions.assertThat;

import cz.cyberrange.platform.guacamole.config.ObjectMapperConfiguration;
import cz.cyberrange.platform.guacamole.config.WebClientConfiguration;
import cz.cyberrange.platform.guacamole.model.GuacamoleProtocol;
import cz.cyberrange.platform.guacamole.model.dto.VmConnectionDataDto;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class SandboxCommunicationServiceTest {

  private static final String BODY =
      "{\"man_ip\":\"10.0.0.1\",\"man_port\":22,\"host_ip\":\"10.0.0.2\","
          + "\"protocols\":[{\"name\":\"ssh\",\"port\":22}]}";

  private AtomicReference<ClientRequest> sentRequest;
  private SandboxCommunicationService service;

  @BeforeEach
  void setUp() {
    sentRequest = new AtomicReference<>();
    ExchangeFunction exchange =
        request -> {
          sentRequest.set(request);
          return Mono.just(
              ClientResponse.create(HttpStatus.OK)
                  .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                  .body(BODY)
                  .build());
        };
    JsonMapper.Builder jsonMapperBuilder = JsonMapper.builder();
    new ObjectMapperConfiguration().snakeCaseJsonMapperCustomizer().customize(jsonMapperBuilder);
    WebClientConfiguration configuration = new WebClientConfiguration(jsonMapperBuilder.build());
    ReflectionTestUtils.setField(configuration, "sandboxServiceUri", "http://sandbox.test/api");
    service =
        new SandboxCommunicationService(
            configuration.sandboxServiceWebClient().mutate().exchangeFunction(exchange).build());

    Jwt jwt = Jwt.withTokenValue("token-value").header("alg", "none").claim("sub", "user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getConnectionData_sandboxAndNode_requestsTopologyEndpointAndMapsBody() {
    VmConnectionDataDto data = service.getConnectionData("sb-1", "node-a");

    assertThat(sentRequest.get().url().toString())
        .isEqualTo("http://sandbox.test/api/sandboxes/sb-1/topology/node-a");
    assertThat(sentRequest.get().method().name()).isEqualTo("GET");
    assertThat(data.getManIp()).isEqualTo("10.0.0.1");
    assertThat(data.getProtocols())
        .singleElement()
        .satisfies(protocol -> assertThat(protocol.getName()).isEqualTo(GuacamoleProtocol.SSH));
  }

  @Test
  void getConnectionData_reservedCharactersInIds_encodesThemInsideTheirPathSegments() {
    service.getConnectionData("a{b}?c#d%e/f", "n{x}?y#z%w/v");

    assertThat(sentRequest.get().url().getRawPath())
        .isEqualTo("/api/sandboxes/a%7Bb%7D%3Fc%23d%25e%2Ff/topology/n%7Bx%7D%3Fy%23z%25w%2Fv");
    assertThat(sentRequest.get().url().getRawQuery()).isNull();
    assertThat(sentRequest.get().url().getRawFragment()).isNull();
  }
}
