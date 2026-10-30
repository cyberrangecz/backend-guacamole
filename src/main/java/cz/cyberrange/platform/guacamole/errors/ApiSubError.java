package cz.cyberrange.platform.guacamole.errors;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description = "Superclass for classes JavaApiError and PythonApiError",
    oneOf = {JavaApiError.class, PythonApiError.class})
public abstract class ApiSubError {

  public abstract String getMessage();
}
