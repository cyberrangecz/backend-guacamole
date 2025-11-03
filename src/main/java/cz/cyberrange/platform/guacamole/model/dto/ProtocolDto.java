package cz.cyberrange.platform.guacamole.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@EqualsAndHashCode
@Getter
@Setter
@ToString
@Schema(name = "ProtocolDto", description = "Protocol information for Guacamole connection")
public class ProtocolDto {

  @Schema(description = "Short name of the protocol")
  @JsonProperty("name")
  private String name;

  @Schema(description = "Port on which the protocol is available")
  @JsonProperty("port")
  private Integer port;
}
