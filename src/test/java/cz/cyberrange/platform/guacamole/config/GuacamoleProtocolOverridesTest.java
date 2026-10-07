package cz.cyberrange.platform.guacamole.config;

import static org.assertj.core.api.Assertions.assertThat;

import cz.cyberrange.platform.guacamole.model.GuacamoleProtocol;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

class GuacamoleProtocolOverridesTest {

  private static PropertySource<?> source(String name, Map<String, Object> properties) {
    return new MapPropertySource(name, properties);
  }

  @Test
  @DisplayName("forProtocol_keysWithProtocolPrefix_returnsParametersPerProtocol")
  void forProtocol_keysWithProtocolPrefix_returnsParametersPerProtocol() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("rdp.disable-bitmap-caching", "true")
            .withProperty("ssh.font-size", "12");

    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    assertThat(overrides.forProtocol(GuacamoleProtocol.RDP))
        .containsExactly(Map.entry("disable-bitmap-caching", "true"));
    assertThat(overrides.forProtocol(GuacamoleProtocol.SSH))
        .containsExactly(Map.entry("font-size", "12"));
  }

  @Test
  @DisplayName("forProtocol_protocolPrefixInUpperCase_matchesCaseInsensitively")
  void forProtocol_protocolPrefixInUpperCase_matchesCaseInsensitively() {
    MockEnvironment environment = new MockEnvironment().withProperty("VNC.color-depth", "16");

    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    assertThat(overrides.forProtocol(GuacamoleProtocol.VNC)).containsEntry("color-depth", "16");
  }

  @Test
  @DisplayName("forProtocol_protocolWithoutOverrides_returnsEmptyMap")
  void forProtocol_protocolWithoutOverrides_returnsEmptyMap() {
    MockEnvironment environment = new MockEnvironment().withProperty("rdp.width", "800");

    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    assertThat(overrides.forProtocol(GuacamoleProtocol.VNC)).isEmpty();
  }

  @Test
  @DisplayName("forProtocol_keysOutsideSupportedProtocols_areIgnored")
  void forProtocol_keysOutsideSupportedProtocols_areIgnored() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("telnet.port", "23")
            .withProperty("server.port", "8087")
            .withProperty("rdp", "plain")
            .withProperty("rdp.", "empty-parameter")
            .withProperty(".width", "no-protocol");

    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    for (GuacamoleProtocol protocol : GuacamoleProtocol.values()) {
      assertThat(overrides.forProtocol(protocol)).isEmpty();
    }
  }

  @Test
  @DisplayName("forProtocol_nestedParameterName_keepsEverythingAfterFirstDot")
  void forProtocol_nestedParameterName_keepsEverythingAfterFirstDot() {
    MockEnvironment environment = new MockEnvironment().withProperty("rdp.a.b", "x");

    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    assertThat(overrides.forProtocol(GuacamoleProtocol.RDP)).containsExactly(Map.entry("a.b", "x"));
  }

  @Test
  @DisplayName("forProtocol_sameKeyInTwoSources_earlierSourceWins")
  void forProtocol_sameKeyInTwoSources_earlierSourceWins() {
    MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(source("lowPriority", Map.of("rdp.width", "640")));
    environment.getPropertySources().addFirst(source("highPriority", Map.of("rdp.width", "1280")));

    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    assertThat(overrides.forProtocol(GuacamoleProtocol.RDP)).containsEntry("width", "1280");
  }

  @Test
  @DisplayName("forProtocol_keysInDifferentSources_mergesParameters")
  void forProtocol_keysInDifferentSources_mergesParameters() {
    MockEnvironment environment = new MockEnvironment();
    environment.getPropertySources().addFirst(source("first", Map.of("rdp.width", "1")));
    environment.getPropertySources().addFirst(source("second", Map.of("rdp.height", "2")));

    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    assertThat(overrides.forProtocol(GuacamoleProtocol.RDP))
        .containsOnly(Map.entry("width", "1"), Map.entry("height", "2"));
  }

  @Test
  @DisplayName("forProtocol_returnedMap_isImmutable")
  void forProtocol_returnedMap_isImmutable() {
    MockEnvironment environment = new MockEnvironment().withProperty("rdp.width", "1");
    GuacamoleProtocolOverrides overrides = new GuacamoleProtocolOverrides(environment);

    Map<String, String> parameters = overrides.forProtocol(GuacamoleProtocol.RDP);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> parameters.put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
