package cz.cyberrange.platform.guacamole.errors;

public class ApiError extends RuntimeException {

  public ApiError(String message) {
    super(message);
  }
}
