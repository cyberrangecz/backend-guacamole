package cz.cyberrange.platform.guacamole.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cz.cyberrange.platform.guacamole.config.ObjectMapperConfiguration;
import cz.cyberrange.platform.guacamole.model.dto.ProtocolDto;
import cz.cyberrange.platform.guacamole.model.dto.UserRefDto;
import cz.cyberrange.platform.guacamole.model.dto.VmConnectionDataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class GuacamoleProtocolTest {

  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() {
    JsonMapper.Builder builder = JsonMapper.builder();
    new ObjectMapperConfiguration().snakeCaseJsonMapperCustomizer().customize(builder);
    jsonMapper = builder.build();
  }

  @ParameterizedTest
  @CsvSource({"rdp,RDP", "RDP,RDP", "Vnc,VNC", "ssh,SSH", "SSH,SSH"})
  @DisplayName("findByProtocolName_knownNameInAnyCase_returnsMatchingProtocol")
  void findByProtocolName_knownNameInAnyCase_returnsMatchingProtocol(
      String protocolName, GuacamoleProtocol expected) {
    assertThat(GuacamoleProtocol.findByProtocolName(protocolName)).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"telnet", "", " rdp"})
  @NullSource
  @DisplayName("findByProtocolName_unknownOrNullName_returnsEmpty")
  void findByProtocolName_unknownOrNullName_returnsEmpty(String protocolName) {
    assertThat(GuacamoleProtocol.findByProtocolName(protocolName)).isEmpty();
  }

  @Test
  @DisplayName("isGraphical_eachProtocol_isTrueOnlyForRdpAndVnc")
  void isGraphical_eachProtocol_isTrueOnlyForRdpAndVnc() {
    assertThat(GuacamoleProtocol.RDP.isGraphical()).isTrue();
    assertThat(GuacamoleProtocol.VNC.isGraphical()).isTrue();
    assertThat(GuacamoleProtocol.SSH.isGraphical()).isFalse();
  }

  @Test
  @DisplayName("serialization_protocol_writesLowercaseProtocolName")
  void serialization_protocol_writesLowercaseProtocolName() {
    assertThat(jsonMapper.writeValueAsString(GuacamoleProtocol.RDP)).isEqualTo("\"rdp\"");
  }

  @Test
  @DisplayName("deserialization_uppercaseProtocolName_returnsProtocol")
  void deserialization_uppercaseProtocolName_returnsProtocol() {
    assertThat(jsonMapper.readValue("\"SSH\"", GuacamoleProtocol.class))
        .isEqualTo(GuacamoleProtocol.SSH);
  }

  @Test
  @DisplayName("deserialization_unknownProtocolName_throwsException")
  void deserialization_unknownProtocolName_throwsException() {
    assertThatThrownBy(() -> jsonMapper.readValue("\"telnet\"", GuacamoleProtocol.class))
        .isInstanceOf(JacksonException.class)
        .hasMessageContaining("Unknown Guacamole protocol 'telnet'");
  }

  @Test
  @DisplayName("deserialization_connectionDataBody_mapsSnakeCaseFieldsAndProtocols")
  void deserialization_connectionDataBody_mapsSnakeCaseFieldsAndProtocols() {
    VmConnectionDataDto data =
        jsonMapper.readValue(
            "{\"man_ip\":\"10.0.0.1\",\"man_port\":22,\"host_ip\":\"10.0.0.2\","
                + "\"protocols\":[{\"name\":\"rdp\",\"port\":3389}]}",
            VmConnectionDataDto.class);

    ProtocolDto expectedProtocol = new ProtocolDto();
    expectedProtocol.setName(GuacamoleProtocol.RDP);
    expectedProtocol.setPort(3389);
    assertThat(data.getManIp()).isEqualTo("10.0.0.1");
    assertThat(data.getManPort()).isEqualTo(22);
    assertThat(data.getHostIp()).isEqualTo("10.0.0.2");
    assertThat(data.getProtocols()).containsExactly(expectedProtocol);
  }

  @Test
  @DisplayName("deserialization_connectionDataWithoutProtocols_defaultsToEmptyList")
  void deserialization_connectionDataWithoutProtocols_defaultsToEmptyList() {
    VmConnectionDataDto data =
        jsonMapper.readValue("{\"man_ip\":\"10.0.0.1\"}", VmConnectionDataDto.class);

    assertThat(data.getProtocols()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "user_ref_id"})
  @DisplayName("deserialization_userReferenceWithIdAlias_mapsUserReferenceId")
  void deserialization_userReferenceWithIdAlias_mapsUserReferenceId(String idProperty) {
    UserRefDto user =
        jsonMapper.readValue(
            "{\"" + idProperty + "\":7,\"sub\":\"a@b.cz\",\"full_name\":\"Jane Doe\"}",
            UserRefDto.class);

    assertThat(user.getUserRefId()).isEqualTo(7L);
    assertThat(user.getUserRefSub()).isEqualTo("a@b.cz");
    assertThat(user.getUserRefFullName()).isEqualTo("Jane Doe");
  }
}
