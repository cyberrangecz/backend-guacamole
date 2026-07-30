package cz.cyberrange.platform.guacamole.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Enumerates the remote protocols supported by Guacamole. */
public enum GuacamoleProtocol {
  RDP("rdp", true),
  VNC("vnc", true),
  SSH("ssh", false);

  private static final Map<String, GuacamoleProtocol> BY_PROTOCOL_NAME =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  GuacamoleProtocol::getProtocolName, Function.identity()));

  private final String protocolName;
  private final boolean graphical;

  GuacamoleProtocol(String protocolName, boolean graphical) {
    this.protocolName = protocolName;
    this.graphical = graphical;
  }

  /**
   * Finds a protocol by its string name, case-insensitive.
   *
   * @param protocolName The name to look up; comparison is case-insensitive.
   * @return An Optional containing the matched protocol, or empty if the name is unknown or null.
   */
  public static Optional<GuacamoleProtocol> findByProtocolName(String protocolName) {
    return protocolName == null
        ? Optional.empty()
        : Optional.ofNullable(BY_PROTOCOL_NAME.get(protocolName.toLowerCase(Locale.ROOT)));
  }

  /**
   * Deserializes a protocol from its string name in JSON.
   *
   * @param protocolName The protocol name to deserialize.
   * @return The matching enum constant.
   * @throws IllegalArgumentException When the protocol name is unknown.
   */
  @JsonCreator
  private static GuacamoleProtocol fromProtocolName(String protocolName) {
    return findByProtocolName(protocolName)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown Guacamole protocol '%s'".formatted(protocolName)));
  }

  /**
   * Returns the string name of this protocol.
   *
   * @return The protocol name.
   */
  @JsonValue
  public String getProtocolName() {
    return protocolName;
  }

  /**
   * Indicates whether this protocol supports graphical rendering.
   *
   * @return {@code true} for graphical protocols; {@code false} for text-based.
   */
  public boolean isGraphical() {
    return graphical;
  }
}
