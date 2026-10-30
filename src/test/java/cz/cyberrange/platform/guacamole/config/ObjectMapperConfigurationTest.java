package cz.cyberrange.platform.guacamole.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class ObjectMapperConfigurationTest {

  record Sample(String firstName, int secondValue) {}

  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() {
    JsonMapper.Builder builder = JsonMapper.builder();
    new ObjectMapperConfiguration().snakeCaseJsonMapperCustomizer().customize(builder);
    jsonMapper = builder.build();
  }

  @Test
  @DisplayName("customizer_serialization_writesSnakeCasePropertiesInDeclarationOrder")
  void customizer_serialization_writesSnakeCasePropertiesInDeclarationOrder() {
    String json = jsonMapper.writeValueAsString(new Sample("a", 1));

    assertThat(json).containsSubsequence("first_name", "second_value");
  }

  @Test
  @DisplayName("customizer_serialization_indentsOutput")
  void customizer_serialization_indentsOutput() {
    assertThat(jsonMapper.writeValueAsString(new Sample("a", 1))).contains(System.lineSeparator());
  }

  @Test
  @DisplayName("customizer_deserialization_readsSnakeCaseProperties")
  void customizer_deserialization_readsSnakeCaseProperties() {
    Sample sample = jsonMapper.readValue("{\"first_name\":\"a\",\"second_value\":2}", Sample.class);

    assertThat(sample).isEqualTo(new Sample("a", 2));
  }

  @Test
  @DisplayName("customizer_deserialization_rejectsUnknownProperties")
  void customizer_deserialization_rejectsUnknownProperties() {
    assertThatThrownBy(
            () -> jsonMapper.readValue("{\"first_name\":\"a\",\"extra\":1}", Sample.class))
        .isInstanceOf(JacksonException.class);
  }
}
