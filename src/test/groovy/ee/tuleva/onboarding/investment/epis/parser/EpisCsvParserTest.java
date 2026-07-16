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
  void combinesGroupedTwoRowHeaderWhenHeaderRowCountIsTwo() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 07;;;;;;
        Väärtpaber;Jooksev NAV;                       Fondimaksed;;                       Ühekordsed maksed;;Valuuta
        ;;Osakud;Summa;Osakud;Summa;
        Tuleva III Samba Pensionifond;1,4153;7892,021;11169,58;123,456;278,73;EUR
        """;

    EpisCsv result = parser.parse(csv, "Väärtpaber", 2);

    assertThat(result.rows()).hasSize(1);
    Map<String, String> row = result.rows().getFirst();
    assertThat(EpisCsvParser.findValue(row, "väärtpaber"))
        .isEqualTo("Tuleva III Samba Pensionifond");
    assertThat(EpisCsvParser.findValue(row, "jooksev nav")).isEqualTo("1,4153");
    assertThat(EpisCsvParser.findValue(row, "fondimaksed osakud")).isEqualTo("7892,021");
    assertThat(EpisCsvParser.findValue(row, "fondimaksed summa")).isEqualTo("11169,58");
    assertThat(EpisCsvParser.findValue(row, "ühekordsed maksed osakud")).isEqualTo("123,456");
    assertThat(EpisCsvParser.findValue(row, "ühekordsed maksed summa")).isEqualTo("278,73");
    assertThat(EpisCsvParser.findValue(row, "valuuta")).isEqualTo("EUR");
  }

  @Test
  void twoRowHeaderModeKeepsPreHeaderLines() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 07;;;;;;
        Väärtpaber;Jooksev NAV;                       Fondimaksed;;                       Ühekordsed maksed;;Valuuta
        ;;Osakud;Summa;Osakud;Summa;
        Tuleva III Samba Pensionifond;1,4153;7892,021;11169,58;123,456;278,73;EUR
        """;

    EpisCsv result = parser.parse(csv, "Väärtpaber", 2);

    assertThat(result.preHeaderLines())
        .containsExactly("Fondivalitseja: Tuleva Fondid AS;;;;;;", "Kuu: 2026 07;;;;;;");
  }

  @Test
  void singleHeaderRowModeIsUnaffectedByThreeArgOverload() {
    String csv =
        """
        Tehingu liik;ISIN;Osakuid;Summa
        SUB;EE3600109435;100,500;1500,00
        """;

    EpisCsv result = parser.parse(csv, "Tehingu liik", 1);

    assertThat(result.rows()).hasSize(1);
    assertThat(EpisCsvParser.findValue(result.rows().getFirst(), "osakuid")).isEqualTo("100,500");
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
        "'1.234,56', COMMA_DECIMAL, 1234.56",
        "'12.345', COMMA_DECIMAL, 12345",
        "'12.345', PERIOD_DECIMAL, 12.345",
        "'1,234.56', PERIOD_DECIMAL, 1234.56",
        "'100,000', COMMA_DECIMAL, 100.000",
        "'100,000', PERIOD_DECIMAL, 100000",
        "'150000000,000', COMMA_DECIMAL, 150000000.000",
        "'0.80000', COMMA_DECIMAL, 0.80000",
        "'1500,00', COMMA_DECIMAL, 1500.00",
        "'1,5', COMMA_DECIMAL, 1.5",
        "'10 000,50', COMMA_DECIMAL, 10000.50",
        "'1.234.567', COMMA_DECIMAL, 1234567",
        "'1,234,567', PERIOD_DECIMAL, 1234567",
        "'1234.56', PERIOD_DECIMAL, 1234.56",
        "'5%', COMMA_DECIMAL, 5",
        "'0', COMMA_DECIMAL, 0"
      })
  void parseNumberHandlesEstonianAndEnglishFormats(
      String input, DecimalConvention convention, String expected) {
    BigDecimal result = EpisCsvParser.parseNumber(input, convention);

    if (expected == null) {
      assertThat(result).isNull();
    } else {
      assertThat(result).isEqualByComparingTo(expected);
    }
  }

  @Test
  void parseNumberHandlesNull() {
    assertThat(EpisCsvParser.parseNumber(null, DecimalConvention.COMMA_DECIMAL)).isNull();
  }

  @Test
  void parseNumberHandlesBlank() {
    assertThat(EpisCsvParser.parseNumber("", DecimalConvention.COMMA_DECIMAL)).isNull();
    assertThat(EpisCsvParser.parseNumber("   ", DecimalConvention.COMMA_DECIMAL)).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "abc, COMMA_DECIMAL",
    "1.2.3, COMMA_DECIMAL",
    "-, COMMA_DECIMAL",
    "N/A, COMMA_DECIMAL"
  })
  void parseNumberThrowsOnUnparseableValue(String input, DecimalConvention convention) {
    assertThatThrownBy(() -> EpisCsvParser.parseNumber(input, convention))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
