package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeTypeJacksonJsonFormatMapperTest {

  private final RuntimeTypeJacksonJsonFormatMapper mapper =
      new RuntimeTypeJacksonJsonFormatMapper();

  private static final Type MAP_TYPE = new TypeReference<Map<String, Object>>() {}.getType();

  @Test
  void fromString_deserializesRawJson() {
    Map<String, Object> result = mapper.fromString("{\"key\":\"value\"}", MAP_TYPE);

    assertThat(result).containsEntry("key", "value");
  }

  @Test
  void fromString_unwrapsH2DoubleWrappedJson() {
    Map<String, Object> result = mapper.fromString("\"{\\\"key\\\":\\\"value\\\"}\"", MAP_TYPE);

    assertThat(result).containsEntry("key", "value");
  }

  @Test
  void fromString_unwrapsH2DoubleWrappedEmptyObject() {
    Map<String, Object> result = mapper.fromString("\"{}\"", MAP_TYPE);

    assertThat(result).isEmpty();
  }

  @Test
  void toString_serializesLocalDateAsIsoString() {
    var map = Map.<String, Object>of("date", LocalDate.of(2026, 3, 7));

    String json = mapper.toString(map, MAP_TYPE);

    assertThat(json).contains("\"2026-03-07\"");
    assertThat(json).doesNotContain("[2026,3,7]");
  }

  @Test
  void toString_serializesBigDecimalAsPlainNumber() {
    var map = Map.<String, Object>of("amount", new BigDecimal("1000000.00").stripTrailingZeros());

    String json = mapper.toString(map, MAP_TYPE);

    assertThat(json).contains("1000000");
    assertThat(json).doesNotContain("1E+");
  }

  @Test
  void toString_serializesInstantAsIsoString() {
    var map = Map.<String, Object>of("timestamp", Instant.parse("2026-03-07T10:00:00Z"));

    String json = mapper.toString(map, MAP_TYPE);

    assertThat(json).contains("\"2026-03-07T10:00:00Z\"");
  }

  @Test
  void roundTrip_preservesAllTypes() {
    var original =
        Map.<String, Object>of(
            "date", LocalDate.of(2026, 3, 7),
            "instant", Instant.parse("2026-03-07T10:00:00Z"),
            "amount", new BigDecimal("1000000.00").stripTrailingZeros(),
            "text", "hello");

    String json = mapper.toString(original, MAP_TYPE);
    Map<String, Object> deserialized = mapper.fromString(json, MAP_TYPE);

    assertThat(deserialized)
        .containsEntry("date", "2026-03-07")
        .containsEntry("instant", "2026-03-07T10:00:00Z")
        .containsEntry("text", "hello");
    assertThat(new BigDecimal(deserialized.get("amount").toString()))
        .isEqualByComparingTo("1000000");
  }
}
