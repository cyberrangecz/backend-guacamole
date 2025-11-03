package cz.cyberrange.platform.guacamole.service;

import cz.cyberrange.platform.guacamole.errors.CustomWebClientException;
import cz.cyberrange.platform.guacamole.model.dto.UserRefDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class SecurityService {

  private final WebClient userManagementWebClient;

  public SecurityService(
      @Qualifier("userManagementServiceWebClient") WebClient userManagementWebClient) {
    this.userManagementWebClient = userManagementWebClient;
  }

  /**
   * Has role boolean.
   *
   * @param roleTypeSecurity the role type security
   * @return the boolean
   */
  public boolean hasRole(Role roleTypeSecurity) {
    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    for (GrantedAuthority gA : authentication.getAuthorities()) {
      if (gA.getAuthority().equals(roleTypeSecurity.name())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets user ref id from user and group.
   *
   * @return the user ref id from user and group
   */
  public UserRefDto getUserRefFromUserAndGroup() {
    try {
      return userManagementWebClient
          .get()
          .uri("/users/info")
          .retrieve()
          .bodyToMono(UserRefDto.class)
          .block();
    } catch (CustomWebClientException ex) {
      throw new CustomWebClientException.MicroserviceApiException(
          "Error when calling user management service API to get info about logged in user.", ex);
    }
  }

  public String getBearerToken() {
    JwtAuthenticationToken authentication =
        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    return authentication.getToken().getTokenValue();
  }

  public enum Role {
    ROLE_GUACAMOLE_TRAINEE,
    ROLE_GUACAMOLE_ORGANISER
  }
}
