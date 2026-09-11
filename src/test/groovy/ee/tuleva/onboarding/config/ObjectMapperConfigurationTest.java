package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ObjectMapperConfigurationTest {

  private final ObjectMapperConfiguration configuration = new ObjectMapperConfiguration();

  record WithPrimitiveBoolean(boolean flag) {}

  @Test
  void customizeObjectMapperReturnsACustomizer() {
    assertThat(configuration.customizeObjectMapper()).isNotNull();
  }

  @Test
  void customizeObjectMapperWritesBigDecimalsAsPlainNumbers() {
    JsonMapper jsonMapper = JsonMapperFixture.jsonMapper();

    String json = jsonMapper.writeValueAsString(new BigDecimal("0.0000001"));

    assertThat(json).isEqualTo("0.0000001");
  }

  @Test
  void customizeObjectMapperAllowsNullForPrimitiveFields() {
    JsonMapper jsonMapper = JsonMapperFixture.jsonMapper();

    assertThatNoException()
        .isThrownBy(() -> jsonMapper.readValue("{\"flag\":null}", WithPrimitiveBoolean.class));
    WithPrimitiveBoolean result =
        jsonMapper.readValue("{\"flag\":null}", WithPrimitiveBoolean.class);
    assertThat(result.flag()).isFalse();
  }
}
