package ee.tuleva.onboarding.investment.epis.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EpisCsvParserTest {

  private final EpisCsvParser parser = new EpisCsvParser();

  @Test
  void parsesEstonianFormatWithSemicolonDelimiter() {
    String csv =
        """
        Tehingu liik;ISIN;Osakuid;Summa
        SUB;EE3600109435;100,500;1500,00
        """;

    EpisCsv result = parser.parse(csv, "Tehingu liik");

    assertThat(result.rows()).hasSize(1);
    Map<String, String> row = result.rows().getFirst();
    assertThat(EpisCsvParser.findValue(row, "tehingu liik")).isEqualTo("SUB");
    assertThat(EpisCsvParser.findValue(row, "isin")).isEqualTo("EE3600109435");
    assertThat(EpisCsvParser.findValue(row, "osakuid")).isEqualTo("100,500");
  }

  @Test
  void parsesEnglishFormatWithCommaDelimiter() {
    String csv =
        """
        Tehingu liik,ISIN,Osakuid,Summa
        SUB,EE3600109435,100.500,1500.00
        """;

    EpisCsv result = parser.parse(csv, "Tehingu liik");

    assertThat(result.rows()).hasSize(1);
    Map<String, String> row = result.rows().getFirst();
    assertThat(EpisCsvParser.findValue(row, "osakuid")).isEqualTo("100.500");
    assertThat(EpisCsvParser.findValue(row, "summa")).isEqualTo("1500.00");
  }

  @Test
  void findsHeaderRowDynamicallyAndKeepsPreHeaderLines() {
    String csv =
        """
        Raport;R45;;
        Tehtud;12.06.2026;;
        ;;;
        Tehingu liik;ISIN;Osakuid;Summa
        SUB;EE3600109435;0;1500,00
        """;

    EpisCsv result = parser.parse(csv, "Tehingu liik");

    assertThat(result.preHeaderLines())
        .containsExactly("Raport;R45;;", "Tehtud;12.06.2026;;", ";;;");
    assertThat(result.rows()).hasSize(1);
  }

  @Test
  void matchesHeaderMarkerIgnoringCaseAndWhitespace() {
    String csv =
        """
        TEHINGU  LIIK;ISIN
        SUB;EE3600109435
        """;

    EpisCsv result = parser.parse(csv, "Tehingu liik");

    assertThat(result.rows()).hasSize(1);
  }

  @Test
  void skipsBlankDataRows() {
    String csv =
        """
        Tehingu liik;ISIN
        SUB;EE3600109435
        ;
        RED;EE3600109443
        """;

    EpisCsv result = parser.parse(csv, "Tehingu liik");

    assertThat(result.rows()).hasSize(2);
  }

  @Test
  void supportsQuotedFieldsContainingDelimiter() {
    String csv =
        """
        Väärtpaber,Osakud
        "Tuleva Maailma Aktsiate Pensionifond, EE3600109435",100.5
        """;

    EpisCsv result = parser.parse(csv, "Väärtpaber");

    assertThat(EpisCsvParser.findValue(result.rows().getFirst(), "väärtpaber"))
        .isEqualTo("Tuleva Maailma Aktsiate Pensionifond, EE3600109435");
  }

  @Test
  void throwsWhenHeaderMarkerNotFoundInFirstTenRows() {
    String csv =
        """
        a;b
        c;d
        """;

    assertThatThrownBy(() -> parser.parse(csv, "Tehingu liik"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "NULL",
      value = {
        "'1,5', 1.5",
        "'1234.56', 1234.56",
        "'1,234.56', 1234.56",
        "'-123,45', -123.45",
        "'10 000,50', 10000.50",
        "'5%', 5",
        "'0', 0",
        "'', NULL",
        "'abc', NULL"
      })
  void parseNumberHandlesEstonianAndEnglishFormats(String input, String expected) {
    BigDecimal result = EpisCsvParser.parseNumber(input);

    if (expected == null) {
      assertThat(result).isNull();
    } else {
      assertThat(result).isEqualByComparingTo(expected);
    }
  }

  @Test
  void parseNumberHandlesNull() {
    assertThat(EpisCsvParser.parseNumber(null)).isNull();
  }
}
