package cz.cyberrange.platform.guacamole.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cz.cyberrange.platform.guacamole.errors.CustomWebClientException;
import cz.cyberrange.platform.guacamole.errors.JavaApiError;
import cz.cyberrange.platform.guacamole.errors.PythonApiError;
import cz.cyberrange.platform.guacamole.support.StubHttpServer;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

class WebClientConfigurationTest {

  private static final String JSON = "application/json";

  private StubHttpServer server;
  private WebClient sandboxClient;
  private WebClient userManagementClient;

  @BeforeEach
  void setUp() throws IOException {
    JsonMapper.Builder builder = JsonMapper.builder();
    new ObjectMapperConfiguration().snakeCaseJsonMapperCustomizer().customize(builder);
    server =
        new StubHttpServer()
            .respond("/ok", 200, JSON, "{\"man_ip\":\"1.2.3.4\"}")
            .respond(
                "/sandbox-error",
                404,
                JSON,
                "{\"detail\":\"No sandbox\",\"parameters\":{\"id\":\"7\"}}")
            .respond("/sandbox-text", 500, "text/plain", "boom")
            .respond("/empty-503", 503, JSON, "")
            .respond(
                "/java-error",
                403,
                JSON,
                "{\"timestamp\":\"2026-10-06T11:34:04.071Z\",\"status\":\"FORBIDDEN\","
                    + "\"message\":\"Not allowed\",\"errors\":[\"x\"],\"path\":\"/u\"}")
            .respond("/java-text", 502, "text/plain", "gateway");
    WebClientConfiguration configuration = new WebClientConfiguration(builder.build());
    ReflectionTestUtils.setField(configuration, "sandboxServiceUri", server.baseUrl());
    ReflectionTestUtils.setField(configuration, "userAndGroupURI", server.baseUrl());
    sandboxClient = configuration.sandboxServiceWebClient();
    userManagementClient = configuration.userManagementServiceWebClient();

    Jwt jwt = Jwt.withTokenValue("token-value").header("alg", "none").claim("sub", "user").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    server.close();
  }

  private static String fetch(WebClient client, String path) {
    return client.get().uri(path).retrieve().bodyToMono(String.class).block();
  }

  @Test
  @DisplayName("sandboxServiceWebClient_successfulCall_sendsBearerTokenAndJsonAcceptHeader")
  void sandboxServiceWebClient_successfulCall_sendsBearerTokenAndJsonAcceptHeader() {
    String body = fetch(sandboxClient, "/ok");

    assertThat(body).contains("1.2.3.4");
    assertThat(server.lastRequestHeader("Authorization")).isEqualTo("Bearer token-value");
    assertThat(server.lastRequestHeader("Accept")).isEqualTo("application/json");
  }

  @Test
  @DisplayName("userManagementServiceWebClient_successfulCall_sendsBearerToken")
  void userManagementServiceWebClient_successfulCall_sendsBearerToken() {
    fetch(userManagementClient, "/ok");

    assertThat(server.lastRequestHeader("Authorization")).isEqualTo("Bearer token-value");
  }

  @Test
  @DisplayName("sandboxServiceWebClient_errorWithPythonBody_throwsClientExceptionWithParsedDetail")
  void sandboxServiceWebClient_errorWithPythonBody_throwsClientExceptionWithParsedDetail() {
    assertThatThrownBy(() -> fetch(sandboxClient, "/sandbox-error"))
        .isInstanceOfSatisfying(
            CustomWebClientException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(exception.getApiSubError()).isInstanceOf(PythonApiError.class);
              assertThat(exception.getApiSubError().getMessage()).isEqualTo("No sandbox");
            });
  }

  @Test
  @DisplayName("sandboxServiceWebClient_errorWithNonJsonBody_throwsClientExceptionQuotingBody")
  void sandboxServiceWebClient_errorWithNonJsonBody_throwsClientExceptionQuotingBody() {
    assertThatThrownBy(() -> fetch(sandboxClient, "/sandbox-text"))
        .isInstanceOfSatisfying(
            CustomWebClientException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(exception.getApiSubError().getMessage())
                  .isEqualTo("Could not obtain error detail. Error body is: boom");
            });
  }

  @Test
  @DisplayName("sandboxServiceWebClient_errorWithEmptyBody_throwsClientExceptionWithDefaultDetail")
  void sandboxServiceWebClient_errorWithEmptyBody_throwsClientExceptionWithDefaultDetail() {
    assertThatThrownBy(() -> fetch(sandboxClient, "/empty-503"))
        .isInstanceOfSatisfying(
            CustomWebClientException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(exception.getApiSubError().getMessage())
                  .isEqualTo("No specific detail provided.");
            });
  }

  @Test
  @DisplayName(
      "userManagementServiceWebClient_errorWithJavaBody_throwsClientExceptionWithParsedError")
  void userManagementServiceWebClient_errorWithJavaBody_throwsClientExceptionWithParsedError() {
    assertThatThrownBy(() -> fetch(userManagementClient, "/java-error"))
        .isInstanceOfSatisfying(
            CustomWebClientException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
              assertThat(exception.getApiSubError()).isInstanceOf(JavaApiError.class);
              assertThat(exception.getApiSubError().getMessage()).isEqualTo("Not allowed");
            });
  }

  @Test
  @DisplayName(
      "userManagementServiceWebClient_errorWithNonJsonBody_throwsClientExceptionQuotingBody")
  void userManagementServiceWebClient_errorWithNonJsonBody_throwsClientExceptionQuotingBody() {
    assertThatThrownBy(() -> fetch(userManagementClient, "/java-text"))
        .isInstanceOfSatisfying(
            CustomWebClientException.class,
            exception ->
                assertThat(exception.getApiSubError().getMessage())
                    .isEqualTo("Could not obtain error message. Error body is: gateway"));
  }

  @Test
  @DisplayName(
      "userManagementServiceWebClient_errorWithEmptyBody_throwsClientExceptionWithDefaultMessage")
  void userManagementServiceWebClient_errorWithEmptyBody_throwsClientExceptionWithDefaultMessage() {
    assertThatThrownBy(() -> fetch(userManagementClient, "/empty-503"))
        .isInstanceOfSatisfying(
            CustomWebClientException.class,
            exception ->
                assertThat(exception.getApiSubError().getMessage())
                    .isEqualTo("No specific message provided."));
  }
}
