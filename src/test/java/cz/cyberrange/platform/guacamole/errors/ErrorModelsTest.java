package cz.cyberrange.platform.guacamole.errors;

import static org.assertj.core.api.Assertions.assertThat;

import cz.cyberrange.platform.guacamole.config.ObjectMapperConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class ErrorModelsTest {

  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() {
    JsonMapper.Builder builder = JsonMapper.builder();
    new ObjectMapperConfiguration().snakeCaseJsonMapperCustomizer().customize(builder);
    jsonMapper = builder.build();
  }

  @Test
  @DisplayName("getMessage_javaErrorWithoutMessage_returnsDefaultText")
  void getMessage_javaErrorWithoutMessage_returnsDefaultText() {
    JavaApiError error = jsonMapper.readValue("{}", JavaApiError.class);

    assertThat(error.getMessage()).isEqualTo("No specific message provided.");
  }

  @Test
  @DisplayName("of_messageOnly_createsErrorWithoutStatusAndEmptyPath")
  void of_messageOnly_createsErrorWithoutStatusAndEmptyPath() {
    JavaApiError error = JavaApiError.of("boom");

    assertThat(error.getMessage()).isEqualTo("boom");
    assertThat(error.getStatus()).isNull();
    assertThat(error.getPath()).isEmpty();
    assertThat(error.getErrors()).containsExactly("");
    assertThat(error.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("of_statusMessageErrorsAndPath_keepsAllValues")
  void of_statusMessageErrorsAndPath_keepsAllValues() {
    JavaApiError error =
        JavaApiError.of(HttpStatus.NOT_FOUND, "missing", List.of("a", "b"), "/groups/1");

    assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(error.getMessage()).isEqualTo("missing");
    assertThat(error.getErrors()).containsExactly("a", "b");
    assertThat(error.getPath()).isEqualTo("/groups/1");
  }

  @Test
  @DisplayName("of_singleErrorString_wrapsItIntoSingletonList")
  void of_singleErrorString_wrapsItIntoSingletonList() {
    JavaApiError error = JavaApiError.of(HttpStatus.BAD_REQUEST, "bad", "reason");

    assertThat(error.getErrors()).containsExactly("reason");
    assertThat(error.getPath()).isEmpty();
  }

  @Test
  @DisplayName("deserialization_userAndGroupErrorBody_mapsAllFieldsIgnoringUnknownOnes")
  void deserialization_userAndGroupErrorBody_mapsAllFieldsIgnoringUnknownOnes() {
    JavaApiError error =
        jsonMapper.readValue(
            "{\"timestamp\":\"2026-10-06T11:34:04.071Z\",\"status\":\"NOT_FOUND\","
                + "\"message\":\"Group missing\",\"errors\":[\"e1\"],\"path\":\"/groups/9\","
                + "\"entity_error_detail\":{\"entity\":\"IDMGroup\"},\"unknown\":1}",
            JavaApiError.class);

    assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(error.getMessage()).isEqualTo("Group missing");
    assertThat(error.getErrors()).containsExactly("e1");
    assertThat(error.getPath()).isEqualTo("/groups/9");
  }

  @Test
  @DisplayName("equals_errorsWithSameValues_areEqualWithSameHashCode")
  void equals_errorsWithSameValues_areEqualWithSameHashCode() {
    JavaApiError first =
        jsonMapper.readValue(
            "{\"timestamp\":\"2026-10-06T11:34:04.071Z\",\"message\":\"m\",\"path\":\"/p\"}",
            JavaApiError.class);
    JavaApiError second =
        jsonMapper.readValue(
            "{\"timestamp\":\"2026-10-06T11:34:04.071Z\",\"message\":\"m\",\"path\":\"/p\"}",
            JavaApiError.class);

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  @DisplayName("equals_errorsWithDifferentMessages_areNotEqual")
  void equals_errorsWithDifferentMessages_areNotEqual() {
    assertThat(JavaApiError.of("one")).isNotEqualTo(JavaApiError.of("two"));
  }

  @Test
  @DisplayName("equals_twoErrorsWithoutMessage_areEqual")
  void equals_twoErrorsWithoutMessage_areEqual() {
    JavaApiError first = jsonMapper.readValue("{}", JavaApiError.class);
    JavaApiError second = jsonMapper.readValue("{}", JavaApiError.class);

    assertThat(first).isEqualTo(second);
  }

  @Test
  @DisplayName("getMessage_pythonErrorWithoutDetail_returnsDefaultText")
  void getMessage_pythonErrorWithoutDetail_returnsDefaultText() {
    PythonApiError error = jsonMapper.readValue("{}", PythonApiError.class);

    assertThat(error.getMessage()).isEqualTo("No specific message provided.");
  }

  @Test
  @DisplayName("deserialization_sandboxErrorBody_mapsDetailAndParameters")
  void deserialization_sandboxErrorBody_mapsDetailAndParameters() {
    PythonApiError error =
        jsonMapper.readValue(
            "{\"detail\":\"Sandbox could not be found.\",\"parameters\":{\"name\":\"sandbox\"},"
                + "\"extra\":true}",
            PythonApiError.class);

    assertThat(error.getMessage()).isEqualTo("Sandbox could not be found.");
    assertThat(error.getParameters()).containsExactly(Map.entry("name", "sandbox"));
  }

  @Test
  @DisplayName("equals_pythonErrorsWithSameDetailAndParameters_areEqual")
  void equals_pythonErrorsWithSameDetailAndParameters_areEqual() {
    PythonApiError first = PythonApiError.of("d", Map.of("k", "v"));
    PythonApiError second = PythonApiError.of("d", Map.of("k", "v"));

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    assertThat(first).isNotEqualTo(PythonApiError.of("other", Map.of("k", "v")));
  }

  @Test
  @DisplayName("constructor_statusAndSubError_exposesBoth")
  void constructor_statusAndSubError_exposesBoth() {
    PythonApiError detail = PythonApiError.of("gone");

    CustomWebClientException exception = new CustomWebClientException(HttpStatus.GONE, detail);

    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(exception.getApiSubError()).isSameAs(detail);
  }

  @Test
  @DisplayName("microserviceApiException_statusAndSubError_buildsDefaultMessageWithDetail")
  void microserviceApiException_statusAndSubError_buildsDefaultMessageWithDetail() {
    CustomWebClientException.MicroserviceApiException exception =
        new CustomWebClientException.MicroserviceApiException(
            HttpStatus.BAD_GATEWAY, PythonApiError.of("upstream down"));

    assertThat(exception.getMessage())
        .isEqualTo("Error when calling external microservice. upstream down");
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
  }

  @Test
  @DisplayName(
      "microserviceApiException_wrappedClientException_keepsStatusSubErrorAndCustomMessage")
  void microserviceApiException_wrappedClientException_keepsStatusSubErrorAndCustomMessage() {
    JavaApiError detail = JavaApiError.of("no user");
    CustomWebClientException cause = new CustomWebClientException(HttpStatus.NOT_FOUND, detail);

    CustomWebClientException.MicroserviceApiException exception =
        new CustomWebClientException.MicroserviceApiException("Lookup failed.", cause);

    assertThat(exception.getMessage()).isEqualTo("Lookup failed. no user");
    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(exception.getApiSubError()).isSameAs(detail);
  }

  @Test
  @DisplayName("apiError_message_isExposedAsRuntimeExceptionMessage")
  void apiError_message_isExposedAsRuntimeExceptionMessage() {
    assertThat(new ApiError("failed")).hasMessage("failed");
  }

  @Test
  @DisplayName("entityErrorDetail_classIdentifierAndValue_storesSimpleClassName")
  void entityErrorDetail_classIdentifierAndValue_storesSimpleClassName() {
    EntityErrorDetail detail =
        new EntityErrorDetail(String.class, "id", Long.class, 5L, "already exists");

    assertThat(detail.getEntity()).isEqualTo("String");
    assertThat(detail.getIdentifier()).isEqualTo("id");
    assertThat(detail.getIdentifierValue()).isEqualTo(5L);
    assertThat(detail.getReason()).isEqualTo("already exists");
  }

  @Test
  @DisplayName("entityErrorDetail_identifierValueOfWrongType_throwsClassCastException")
  void entityErrorDetail_identifierValueOfWrongType_throwsClassCastException() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new EntityErrorDetail(String.class, "id", Long.class, "text", "reason"))
        .isInstanceOf(ClassCastException.class);
  }

  @Test
  @DisplayName("equals_detailsWithSameFields_areEqualWithSameHashCode")
  void equals_detailsWithSameFields_areEqualWithSameHashCode() {
    EntityErrorDetail first = new EntityErrorDetail(String.class, "reason");
    EntityErrorDetail second = new EntityErrorDetail(String.class, "reason");

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    assertThat(first).isNotEqualTo(new EntityErrorDetail(Integer.class, "reason"));
  }

  @Test
  @DisplayName("serialization_detailWithoutIdentifier_omitsNullProperties")
  void serialization_detailWithoutIdentifier_omitsNullProperties() {
    String json = jsonMapper.writeValueAsString(new EntityErrorDetail("only reason"));

    assertThat(json).contains("reason").doesNotContain("identifier").doesNotContain("entity");
  }
}
