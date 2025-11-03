package cz.cyberrange.platform.guacamole.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
@Schema(
    name = "VmConnectionDataDto",
    description = "Fetch information required for establishing Guacamole connection")
public class VmConnectionDataDto {

  @Schema(description = "Management IP of the sandbox", example = "10.0.0.2")
  @JsonProperty("man_ip")
  private String manIp;

  @Schema(description = "Management port of the sandbox", example = "22")
  @JsonProperty("man_port")
  private Integer manPort;

  @Schema(description = "IP of the host inside VM", example = "10.0.0.2")
  @JsonProperty("host_ip")
  private String hostIp;

  @Schema(
      description = "Array of available remote connection protocols",
      example = "[{\"name\":\"rdp\", \"port\":3389}, {\"name\":\"ssh\", \"port\":22}]")
  @JsonProperty("protocols")
  private List<ProtocolDto> protocols = java.util.Collections.emptyList();
}
