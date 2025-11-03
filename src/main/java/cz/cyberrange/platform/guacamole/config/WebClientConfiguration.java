package cz.cyberrange.platform.guacamole.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cyberrange.platform.guacamole.errors.CustomWebClientException;
import cz.cyberrange.platform.guacamole.errors.JavaApiError;
import cz.cyberrange.platform.guacamole.errors.PythonApiError;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** The type Web client config. */
@Import(ObjectMapperConfiguration.class)
@Configuration
public class WebClientConfiguration {

  private final ObjectMapper objectMapper;

  @Value("${sandbox-service.uri}")
  private String sandboxServiceUri;

  @Value("${user-and-group-server.uri}")
  private String userAndGroupURI;

  @Value("${elasticsearch-service.uri}")
  private String elasticsearchServiceURI;

  public WebClientConfiguration(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Openstack service web client.
   *
   * @return the web client
   */
  @Bean
  public WebClient sandboxServiceWebClient() {
    return WebClient.builder()
        .baseUrl(sandboxServiceUri)
        .defaultHeaders(
            headers -> {
              headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
              headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            })
        .codecs(
            configurer -> {
              configurer
                  .defaultCodecs()
                  .jackson2JsonEncoder(
                      new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
              configurer
                  .defaultCodecs()
                  .jackson2JsonDecoder(
                      new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
            })
        .filters(
            exchangeFilterFunctions -> {
              exchangeFilterFunctions.add(addSecurityHeader());
              exchangeFilterFunctions.add(openStackSandboxServiceExceptionHandlingFunction());
            })
        .build();
  }

  /**
   * User management service web client web client.
   *
   * @return the web client
   */
  @Bean
  public WebClient userManagementServiceWebClient() {
    return WebClient.builder()
        .baseUrl(userAndGroupURI)
        .defaultHeaders(
            headers -> {
              headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
              headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            })
        .filters(
            exchangeFilterFunctions -> {
              exchangeFilterFunctions.add(addSecurityHeader());
              exchangeFilterFunctions.add(javaMicroserviceExceptionHandlingFunction());
            })
        .build();
  }

  /**
   * Elasticsearch service web client.
   *
   * @return the web client
   */
  @Bean
  public WebClient elasticsearchServiceWebClient() {
    return WebClient.builder()
        .baseUrl(elasticsearchServiceURI)
        .defaultHeaders(
            headers -> {
              headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
              headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            })
        .filters(
            exchangeFilterFunctions -> {
              exchangeFilterFunctions.add(addSecurityHeader());
              exchangeFilterFunctions.add(javaMicroserviceExceptionHandlingFunction());
            })
        .exchangeStrategies(
            ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build())
        .build();
  }

  private ExchangeFilterFunction addSecurityHeader() {
    return (request, next) -> {
      JwtAuthenticationToken jwtAuthentication =
          (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
      Jwt jwtToken = jwtAuthentication.getToken();
      ClientRequest filtered =
          ClientRequest.from(request)
              .header("Authorization", "Bearer " + jwtToken.getTokenValue())
              .build();
      return next.exchange(filtered);
    };
  }

  private ExchangeFilterFunction openStackSandboxServiceExceptionHandlingFunction() {
    return ExchangeFilterFunction.ofResponseProcessor(
        clientResponse -> {
          if (clientResponse.statusCode().is4xxClientError()
              || clientResponse.statusCode().is5xxServerError()) {
            return clientResponse
                .bodyToMono(String.class)
                .flatMap(
                    errorBody -> {
                      PythonApiError pythonApiError = obtainSuitablePythonApiError(errorBody);
                      return Mono.error(
                          new CustomWebClientException(
                              clientResponse.statusCode(), pythonApiError));
                    });
          } else {
            return Mono.just(clientResponse);
          }
        });
  }

  private PythonApiError obtainSuitablePythonApiError(String errorBody) {
    if (errorBody == null || errorBody.isBlank()) {
      return PythonApiError.of("No specific detail provided.");
    }
    try {
      return objectMapper.readValue(errorBody, PythonApiError.class);
    } catch (IOException e) {
      return PythonApiError.of("Could not obtain error detail. Error body is: " + errorBody);
    }
  }

  private ExchangeFilterFunction javaMicroserviceExceptionHandlingFunction() {
    return ExchangeFilterFunction.ofResponseProcessor(
        clientResponse -> {
          if (clientResponse.statusCode().is4xxClientError()
              || clientResponse.statusCode().is5xxServerError()) {
            return clientResponse
                .bodyToMono(String.class)
                .flatMap(
                    errorBody -> {
                      JavaApiError javaApiError = obtainSuitableJavaApiError(errorBody);
                      return Mono.error(
                          new CustomWebClientException(clientResponse.statusCode(), javaApiError));
                    });
          } else {
            return Mono.just(clientResponse);
          }
        });
  }

  private JavaApiError obtainSuitableJavaApiError(String errorBody) {
    if (errorBody == null || errorBody.isBlank()) {
      return JavaApiError.of("No specific message provided.");
    }
    try {
      return objectMapper.readValue(errorBody, JavaApiError.class);
    } catch (IOException e) {
      return JavaApiError.of("Could not obtain error message. Error body is: " + errorBody);
    }
  }
}
