package ee.tuleva.onboarding.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class H2JsonFunctionsTest {

  @Test
  void jsonBuildObject_shouldReturnEmptyObject_whenNoArgs() {
    String result = H2JsonFunctions.jsonBuildObject();
    assertThat(result).isEqualTo("{}");
  }

  @Test
  void jsonBuildObject_shouldReturnEmptyObject_whenNullArgs() {
    String result = H2JsonFunctions.jsonBuildObject((String[]) null);
    assertThat(result).isEqualTo("{}");
  }

  @Test
  void jsonBuildObject_shouldThrowException_whenOddNumberOfArgs() {
    assertThrows(
        IllegalArgumentException.class, () -> H2JsonFunctions.jsonBuildObject("key1", "value1", "key2"));
  }

  @Test
  void jsonBuildObject_shouldHandleIntegerValues() {
    String result = H2JsonFunctions.jsonBuildObject("intValue", "42", "negativeInt", "-123");
    assertThat(result).isEqualTo("{\"intValue\":42,\"negativeInt\":-123}");
  }

  @Test
  void jsonBuildObject_shouldHandleLongValues() {
    String result =
        H2JsonFunctions.jsonBuildObject(
            "longValue", "9223372036854775807", "negativeLong", "-9223372036854775808");
    assertThat(result)
        .isEqualTo("{\"longValue\":9223372036854775807,\"negativeLong\":-9223372036854775808}");
  }

  @Test
  void jsonBuildObject_shouldHandleDoubleValues() {
    String result =
        H2JsonFunctions.jsonBuildObject(
            "doubleValue", "3.14159", "negativeDouble", "-2.71828", "scientific", "1.23e10");
    assertThat(result)
        .isEqualTo(
            "{\"doubleValue\":3.14159,\"negativeDouble\":-2.71828,\"scientific\":1.23e10}");
  }

  @Test
  void jsonBuildObject_shouldHandleZeroValues() {
    String result = H2JsonFunctions.jsonBuildObject("zero", "0", "zeroDouble", "0.0");
    assertThat(result).isEqualTo("{\"zero\":0,\"zeroDouble\":0.0}");
  }

  @Test
  void jsonBuildObject_shouldHandleStringValues() {
    String result = H2JsonFunctions.jsonBuildObject("name", "John Doe", "version", "2.0");
    assertThat(result).isEqualTo("{\"name\":\"John Doe\",\"version\":\"2.0\"}");
  }

  @Test
  void jsonBuildObject_shouldHandleBooleanValues() {
    String result =
        H2JsonFunctions.jsonBuildObject("isActive", "true", "isDeleted", "false", "UPPERCASE", "TRUE");
    assertThat(result).isEqualTo("{\"isActive\":true,\"isDeleted\":false,\"UPPERCASE\":true}");
  }

  @Test
  void jsonBuildObject_shouldHandleNullValues() {
    String result = H2JsonFunctions.jsonBuildObject("nullValue", "null", "explicitNull", null);
    assertThat(result).isEqualTo("{\"nullValue\":null,\"explicitNull\":null}");
  }

  @Test
  void jsonBuildObject_shouldHandleAlreadyQuotedStrings() {
    String result = H2JsonFunctions.jsonBuildObject("quotedString", "\"already quoted\"");
    assertThat(result).isEqualTo("{\"quotedString\":\"already quoted\"}");
  }

  @Test
  void jsonBuildObject_shouldHandleNestedObjects() {
    String result = H2JsonFunctions.jsonBuildObject("nested", "{\"key\":\"value\"}");
    assertThat(result).isEqualTo("{\"nested\":{\"key\":\"value\"}}");
  }

  @Test
  void jsonBuildObject_shouldHandleArrays() {
    String result = H2JsonFunctions.jsonBuildObject("array", "[1,2,3]");
    assertThat(result).isEqualTo("{\"array\":[1,2,3]}");
  }

  @Test
  void jsonBuildObject_shouldHandleMixedTypes() {
    String result =
        H2JsonFunctions.jsonBuildObject(
            "intValue",
            "42",
            "longValue",
            "9223372036854775807",
            "doubleValue",
            "3.14",
            "stringValue",
            "text",
            "boolValue",
            "true",
            "nullValue",
            "null");
    assertThat(result)
        .isEqualTo(
            "{\"intValue\":42,\"longValue\":9223372036854775807,\"doubleValue\":3.14,\"stringValue\":\"text\",\"boolValue\":true,\"nullValue\":null}");
  }

  @Test
  void jsonBuildObject_shouldHandleAmlRiskScenario() {
    // Simulates the actual usage in AmlRiskTestDataFixtures
    String result =
        H2JsonFunctions.jsonBuildObject(
            "version",
            "2.0",
            "level",
            "1",
            "attribute_1",
            "1",
            "attribute_2",
            "2",
            "attribute_3",
            "3",
            "attribute_4",
            "4",
            "attribute_5",
            "5");
    assertThat(result)
        .isEqualTo(
            "{\"version\":\"2.0\",\"level\":1,\"attribute_1\":1,\"attribute_2\":2,\"attribute_3\":3,\"attribute_4\":4,\"attribute_5\":5}");
  }
}
