package ee.tuleva.onboarding.investment.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvToJsonConverterTest {

  private final CsvToJsonConverter converter = new CsvToJsonConverter();

  @Test
  void convert_parsesSemicolonDelimitedCsv() {
    String csv = "Name;Amount;Date\nTest;1000.50;2026-01-15";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    List<Map<String, Object>> result = converter.convert(inputStream, ';');

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().get("Name")).isEqualTo("Test");
    assertThat(result.getFirst().get("Amount")).isEqualTo(new BigDecimal("1000.50"));
    assertThat(result.getFirst().get("Date")).isEqualTo("2026-01-15");
  }

  @Test
  void convert_parsesCommaDelimitedCsv() {
    String csv = "Name,Amount\nTest,500";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    List<Map<String, Object>> result = converter.convert(inputStream, ',');

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().get("Name")).isEqualTo("Test");
    assertThat(result.getFirst().get("Amount")).isEqualTo(new BigDecimal("500"));
  }

  @Test
  void convert_handlesEmptyValues() {
    String csv = "Name;Amount\n;";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    List<Map<String, Object>> result = converter.convert(inputStream, ';');

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().get("Name")).isNull();
    assertThat(result.getFirst().get("Amount")).isNull();
  }

  @Test
  void convert_handlesMultipleRows() {
    String csv = "Name;Amount\nFirst;100\nSecond;200\nThird;300";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    List<Map<String, Object>> result = converter.convert(inputStream, ';');

    assertThat(result).hasSize(3);
    assertThat(result.get(0).get("Name")).isEqualTo("First");
    assertThat(result.get(1).get("Name")).isEqualTo("Second");
    assertThat(result.get(2).get("Name")).isEqualTo("Third");
  }

  @Test
  void convert_handlesEuropeanNumberFormat() {
    String csv = "Amount\n1 000,50";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    List<Map<String, Object>> result = converter.convert(inputStream, ';');

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().get("Amount")).isEqualTo(new BigDecimal("1000.50"));
  }

  @Test
  void convert_preservesNonNumericStrings() {
    String csv = "ISIN;Name\nIE00BFG1TM61;iShares ETF";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    List<Map<String, Object>> result = converter.convert(inputStream, ';');

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().get("ISIN")).isEqualTo("IE00BFG1TM61");
    assertThat(result.getFirst().get("Name")).isEqualTo("iShares ETF");
  }

  @Test
  void convert_returnsEmptyListForEmptyCsv() {
    String csv = "Name;Amount";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    List<Map<String, Object>> result = converter.convert(inputStream, ';');

    assertThat(result).isEmpty();
  }
}
