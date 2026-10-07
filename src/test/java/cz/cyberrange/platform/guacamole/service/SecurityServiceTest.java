package cz.cyberrange.platform.guacamole.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cz.cyberrange.platform.guacamole.config.ObjectMapperConfiguration;
import cz.cyberrange.platform.guacamole.config.WebClientConfiguration;
import cz.cyberrange.platform.guacamole.errors.CustomWebClientException;
import cz.cyberrange.platform.guacamole.model.dto.UserRefDto;
import cz.cyberrange.platform.guacamole.service.SecurityService.Role;
import cz.cyberrange.platform.guacamole.support.StubHttpServer;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class SecurityServiceTest {

  private StubHttpServer server;
  private SecurityService securityService;

  @BeforeEach
  void setUp() throws IOException {
    JsonMapper.Builder builder = JsonMapper.builder();
    new ObjectMapperConfiguration().snakeCaseJsonMapperCustomizer().customize(builder);
    server =
        new StubHttpServer()
            .respond(
                "/users/info",
                200,
                "application/json",
                "{\"id\":3,\"sub\":\"a@b.cz\",\"full_name\":\"Jane Doe\",\"mail\":\"a@b.cz\"}");
    WebClientConfiguration configuration = new WebClientConfiguration(builder.build());
    ReflectionTestUtils.setField(configuration, "userAndGroupURI", server.baseUrl());
    securityService = new SecurityService(configuration.userManagementServiceWebClient());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    server.close();
  }

  private static void authenticate(String... authorities) {
    Jwt jwt = Jwt.withTokenValue("jwt-value").header("alg", "none").claim("sub", "user").build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt, List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
  }

  @Test
  @DisplayName("hasRole_callerHoldsRole_returnsTrue")
  void hasRole_callerHoldsRole_returnsTrue() {
    authenticate("ROLE_GUACAMOLE_TRAINEE");

    assertThat(securityService.hasRole(Role.ROLE_GUACAMOLE_TRAINEE)).isTrue();
  }

  @Test
  @DisplayName("hasRole_callerHoldsOtherRoleOnly_returnsFalse")
  void hasRole_callerHoldsOtherRoleOnly_returnsFalse() {
    authenticate("ROLE_GUACAMOLE_TRAINEE");

    assertThat(securityService.hasRole(Role.ROLE_GUACAMOLE_ORGANISER)).isFalse();
  }

  @Test
  @DisplayName("hasRole_callerWithoutAuthorities_returnsFalse")
  void hasRole_callerWithoutAuthorities_returnsFalse() {
    authenticate();

    assertThat(securityService.hasRole(Role.ROLE_GUACAMOLE_ORGANISER)).isFalse();
  }

  @Test
  @DisplayName("getBearerToken_authenticatedCaller_returnsTokenValue")
  void getBearerToken_authenticatedCaller_returnsTokenValue() {
    authenticate();

    assertThat(securityService.getBearerToken()).isEqualTo("jwt-value");
  }

  @Test
  @DisplayName("getUserRefFromUserAndGroup_successfulResponse_mapsUserReference")
  void getUserRefFromUserAndGroup_successfulResponse_mapsUserReference() {
    authenticate();

    UserRefDto user = securityService.getUserRefFromUserAndGroup();

    assertThat(user.getUserRefId()).isEqualTo(3L);
    assertThat(user.getUserRefSub()).isEqualTo("a@b.cz");
    assertThat(user.getUserRefFullName()).isEqualTo("Jane Doe");
    assertThat(user.getMail()).isEqualTo("a@b.cz");
  }

  @Test
  @DisplayName(
      "getUserRefFromUserAndGroup_serviceRejectsCall_throwsMicroserviceApiExceptionWithStatus")
  void getUserRefFromUserAndGroup_serviceRejectsCall_throwsMicroserviceApiExceptionWithStatus() {
    server.respond("/users/info", 401, "application/json", "{\"message\":\"Expired\"}");
    authenticate();

    assertThatThrownBy(() -> securityService.getUserRefFromUserAndGroup())
        .isInstanceOfSatisfying(
            CustomWebClientException.MicroserviceApiException.class,
            exception -> {
              assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
              assertThat(exception.getMessage())
                  .isEqualTo(
                      "Error when calling user management service API to get info about logged in user. Expired");
            });
  }
}
