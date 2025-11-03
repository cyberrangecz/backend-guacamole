package cz.cyberrange.platform.guacamole.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@EqualsAndHashCode
@Getter
@Setter
@ToString
@Schema(
    name = "UserRefDTO",
    description =
        "User information from user-and-group microservice is mapped to this class and is also used to provide information about authors, participants, and organizers.")
public class UserRefDto {

  @JsonAlias({"id", "user_ref_id"})
  @Schema(description = "Reference to user in another microservice and get his id", example = "1")
  private Long userRefId;

  @JsonProperty("sub")
  @Schema(
      description = "Reference to user in another microservice.",
      example = "999999@mail.example.cz")
  private String userRefSub;

  @JsonProperty("full_name")
  @Schema(
      description = "Reference to user in another microservice and get his full name",
      example = "Mgr. John Doe")
  private String userRefFullName;

  @JsonProperty("given_name")
  @Schema(description = "User given name", example = "John")
  private String userRefGivenName;

  @JsonProperty("family_name")
  @Schema(description = "User family name", example = "Doe")
  private String userRefFamilyName;

  @Schema(
      description = "Reference to user in another microservice and get his iss",
      example = "https://oidc.provider.cz")
  private String iss;

  @Schema(
      description = "Identicon of a user encoded in base64.",
      example = "iVBORw0KGgoAAAANSUhEUgAA...")
  private byte[] picture;

  @Schema(description = "Email of the user.", example = "johndoe@mail.example.cz")
  private String mail;
}
