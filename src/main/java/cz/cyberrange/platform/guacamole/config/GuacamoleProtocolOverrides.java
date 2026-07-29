package cz.cyberrange.platform.guacamole.config;

import cz.cyberrange.platform.guacamole.model.GuacamoleProtocol;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Holds user-defined Guacamole connection parameter overrides parsed once from the environment.
 * Keys use the form {@code <protocol>.<guacamole-parameter>} (e.g. {@code
 * rdp.disable-bitmap-caching=true}). Only keys whose prefix names a supported protocol are
 * collected, case-insensitively; the parameter set itself is not restricted.
 */
@Component
@Slf4j
public class GuacamoleProtocolOverrides {

  private final Map<GuacamoleProtocol, Map<String, String>> overridesByProtocol;

  /**
   * Instantiates a new Guacamole protocol overrides holder, parsing all enumerable property
   * sources. Property source ordering follows Spring semantics: a key defined in an earlier source
   * wins.
   *
   * @param environment the Spring environment holding all property sources
   */
  public GuacamoleProtocolOverrides(ConfigurableEnvironment environment) {
    this.overridesByProtocol = parseOverrides(environment);
    log.info("Parsed Guacamole protocol overrides: {}", overridesByProtocol);
  }

  private static Map<GuacamoleProtocol, Map<String, String>> parseOverrides(
      ConfigurableEnvironment environment) {
    Map<GuacamoleProtocol, Map<String, String>> parsed = new EnumMap<>(GuacamoleProtocol.class);
    for (PropertySource<?> source : environment.getPropertySources()) {
      if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
        continue;
      }
      for (String key : enumerable.getPropertyNames()) {
        int separator = key.indexOf('.');
        if (separator <= 0 || separator == key.length() - 1) {
          continue;
        }
        Optional<GuacamoleProtocol> protocol =
            GuacamoleProtocol.findByProtocolName(key.substring(0, separator));
        if (protocol.isEmpty()) {
          continue;
        }
        String parameter = key.substring(separator + 1);
        parsed
            .computeIfAbsent(protocol.get(), name -> new HashMap<>())
            .putIfAbsent(parameter, String.valueOf(enumerable.getProperty(key)));
      }
    }
    parsed.replaceAll((protocol, parameters) -> Map.copyOf(parameters));
    return parsed;
  }

  /**
   * Returns the parsed {@code <guacamole-parameter>=<value>} entries for the given protocol.
   *
   * @param protocol protocol whose overrides to return
   * @return map of Guacamole parameter name to configured value; empty when none defined
   */
  public Map<String, String> forProtocol(GuacamoleProtocol protocol) {
    return overridesByProtocol.getOrDefault(protocol, Map.of());
  }
}
