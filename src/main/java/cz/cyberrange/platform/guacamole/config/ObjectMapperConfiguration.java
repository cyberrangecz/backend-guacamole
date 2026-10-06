package cz.cyberrange.platform.guacamole.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.EnumFeature;

/** Configures the auto-configured JSON mapper. */
@Configuration
public class ObjectMapperConfiguration {

  /**
   * Names JSON properties in snake_case, reads and writes enums by their constant name, keeps
   * properties in declaration order, indents written JSON, and rejects JSON carrying properties the
   * target type does not declare.
   *
   * @return the customizer applied to the auto-configured JSON mapper
   */
  @Bean
  public JsonMapperBuilderCustomizer snakeCaseJsonMapperCustomizer() {
    return builder ->
        builder
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
            .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
