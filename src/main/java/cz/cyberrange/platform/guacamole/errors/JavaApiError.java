package cz.cyberrange.platform.guacamole.errors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

@Schema(
    name = "JavaApiError",
    description = "A detailed error from another Java microservice.",
    allOf = {ApiSubError.class})
@JsonIgnoreProperties(ignoreUnknown = true)
public class JavaApiError extends ApiSubError {

  @Getter
  @Setter
  @Schema(
      description = "The time when the exception occurred, as a UTC instant",
      example = "2026-10-06T11:34:04.071Z")
  private Instant timestamp;

  @Setter
  @Schema(
      description = "The specific description of the ApiError.",
      example = "The IDMGroup could not be found in database")
  private String message;

  @Setter
  @Getter
  @Schema(description = "The HTTP response status code", example = "404 NOT_FOUND")
  private HttpStatus status;

  @Setter
  @Getter
  @Schema(
      description = "The list of main reasons of the ApiError.",
      example = "[\"The requested resource was not found\"]")
  private List<String> errors;

  @Setter
  @Getter
  @Schema(
      description = "The requested URI path which caused error.",
      example = "/user-and-group/api/v1/groups/1000")
  private String path;

  @Schema(description = "Entity detail related to the error.")
  @JsonProperty("entity_error_detail")
  private EntityErrorDetail entityErrorDetail;

  @ConstructorProperties({"message"})
  private JavaApiError(String message) {
    this.message = message;
  }

  public static JavaApiError of(
      HttpStatus httpStatus, String message, List<String> errors, String path) {
    JavaApiError apiError = new JavaApiError(message);
    apiError.setStatus(httpStatus);
    apiError.setTimestamp(Instant.now());
    apiError.setErrors(errors);
    apiError.setPath(path);
    return apiError;
  }

  public static JavaApiError of(HttpStatus httpStatus, String message, String error, String path) {
    JavaApiError apiError = new JavaApiError(message);
    apiError.setStatus(httpStatus);
    apiError.setTimestamp(Instant.now());
    apiError.setError(error);
    apiError.setPath(path);
    return apiError;
  }

  public static JavaApiError of(HttpStatus httpStatus, String message, List<String> errors) {
    return JavaApiError.of(httpStatus, message, errors, "");
  }

  public static JavaApiError of(HttpStatus httpStatus, String message, String error) {
    return JavaApiError.of(httpStatus, message, error, "");
  }

  public static JavaApiError of(HttpStatus httpStatus, String message) {
    return JavaApiError.of(httpStatus, message, "", "");
  }

  public static JavaApiError of(String message) {
    return JavaApiError.of(null, message, "", "");
  }

  @Override
  public String getMessage() {
    return message == null ? "No specific message provided." : message;
  }

  public void setError(final String error) {
    errors = Collections.singletonList(error);
  }

  @Override
  public String toString() {
    return "ApiError{"
        + "timestamp="
        + timestamp
        + ", status="
        + getStatus()
        + ", message='"
        + message
        + '\''
        + ", errors="
        + errors
        + ", path='"
        + path
        + '\''
        + ", entityErrorDetail="
        + entityErrorDetail
        + '}';
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, getStatus(), getMessage(), errors, path);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!(obj instanceof JavaApiError other)) return false;
    return Objects.equals(errors, other.getErrors())
        && Objects.equals(getMessage(), other.getMessage())
        && Objects.equals(path, other.getPath())
        && Objects.equals(getStatus(), other.getStatus())
        && Objects.equals(timestamp, other.getTimestamp());
  }
}
