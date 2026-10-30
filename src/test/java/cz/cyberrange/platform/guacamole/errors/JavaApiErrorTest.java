package cz.cyberrange.platform.guacamole.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.cyberrange.platform.guacamole.config.ObjectMapperConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JavaApiErrorTest {

  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() {
    JsonMapper.Builder builder = JsonMapper.builder();
    new ObjectMapperConfiguration().snakeCaseJsonMapperCustomizer().customize(builder);
    jsonMapper = builder.build();
  }

  @Test
  void readsTimestampWrittenAsUtcInstantWithThreeFractionDigits() {
    JavaApiError error =
        jsonMapper.readValue(
            "{\"timestamp\": \"2026-10-06T11:34:04.071Z\", \"status\": \"NOT_FOUND\","
                + " \"message\": \"Missing\", \"errors\": [\"Missing\"], \"path\": \"/groups/1\"}",
            JavaApiError.class);

    assertEquals(Instant.parse("2026-10-06T11:34:04.071Z"), error.getTimestamp());
  }

  @Test
  void readsTimestampOfWholeSecond() {
    JavaApiError error =
        jsonMapper.readValue("{\"timestamp\": \"2026-10-06T11:34:04.000Z\"}", JavaApiError.class);

    assertEquals(Instant.parse("2026-10-06T11:34:04Z"), error.getTimestamp());
  }
}
