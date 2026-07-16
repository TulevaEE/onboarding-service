package ee.tuleva.onboarding.investment.epis.parser;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

class R16ReportParserTest {

  private final R16ReportParser parser = new R16ReportParser(new EpisCsvParser());

  @Test
  void parsesUnitCountsAndPaymentMonthPerFundMappedByIsin() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 06;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        EE3600109435;0,80;1000,000;800,00;5000,000;4000,00;EUR
        EE3600109443;0,70;200,000;140,00;0;0;EUR
        """;

    Map<String, R16ParsedFlow> result = parser.parse(csv);

    assertThat(result).containsOnlyKeys("TUK75", "TUK00");
    R16ParsedFlow tuk75 = result.get("TUK75");
    assertThat(tuk75.fund()).isEqualTo(TUK75);
    assertThat(tuk75.fondimaksedUnits()).isEqualByComparingTo("1000.000");
    assertThat(tuk75.uhekordsedUnits()).isEqualByComparingTo("5000.000");
    assertThat(tuk75.paymentMonth()).isEqualTo(YearMonth.of(2026, 6));
    R16ParsedFlow tuk00 = result.get("TUK00");
    assertThat(tuk00.fund()).isEqualTo(TUK00);
    assertThat(tuk00.fondimaksedUnits()).isEqualByComparingTo("200.000");
    assertThat(tuk00.uhekordsedUnits()).isEqualByComparingTo("0");
    assertThat(tuk00.paymentMonth()).isEqualTo(YearMonth.of(2026, 6));
  }

  @Test
  void mapsFundByDisplayNameWhenVaartpaberIsNotIsin() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 06;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        Tuleva III Samba Pensionifond;0,90;300,000;270,00;100,000;90,00;EUR
        """;

    Map<String, R16ParsedFlow> result = parser.parse(csv);

    assertThat(result).containsOnlyKeys("TUV100");
    assertThat(result.get("TUV100").fund()).isEqualTo(TUV100);
    assertThat(result.get("TUV100").fondimaksedUnits()).isEqualByComparingTo("300.000");
  }

  @Test
  void throwsWhenKuuHeaderRowMissing() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        EE3600109435;0,80;1000,000;800,00;5000,000;4000,00;EUR
        """;

    assertThatThrownBy(() -> parser.parse(csv)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void skipsRowsWithUnknownSecurity() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 06;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        XX0000000000;0,80;1000,000;800,00;5000,000;4000,00;EUR
        """;

    Map<String, R16ParsedFlow> result = parser.parse(csv);

    assertThat(result).isEmpty();
  }

  @Test
  void keepsFundsWithZeroUnits() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 06;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        EE3600109435;0,80;0;0;0;0;EUR
        """;

    Map<String, R16ParsedFlow> result = parser.parse(csv);

    assertThat(result.get("TUK75").fondimaksedUnits()).isEqualByComparingTo("0");
    assertThat(result.get("TUK75").uhekordsedUnits()).isEqualByComparingTo("0");
  }

  @Test
  void accumulatesUnitsAcrossMultipleRowsForSameFund() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 06;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        EE3600109435;0,80;100,000;80,00;0;0;EUR
        EE3600109435;0,80;50,000;40,00;0;0;EUR
        """;

    Map<String, R16ParsedFlow> result = parser.parse(csv);

    assertThat(result.get("TUK75").fondimaksedUnits()).isEqualByComparingTo("150.000");
  }

  @Test
  void doesNotMisinterpretCommaDecimalUnitsThatMatchThousandsGroupingShape() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 06;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        EE3600109435;0,80;196,938;800,00;0;0;EUR
        """;

    Map<String, R16ParsedFlow> result = parser.parse(csv);

    assertThat(result.get("TUK75").fondimaksedUnits()).isEqualByComparingTo("196.938");
  }

  @Test
  void throwsWhenUnitsExceedHundredMillion() {
    String csv =
        """
        Fondivalitseja: Tuleva Fondid AS;;;;;;
        Kuu: 2026 06;;;;;;
        Väärtpaber;Jooksev NAV;Fondimaksed Osakud;Fondimaksed Summa;Ühekordsed maksed Osakud;Ühekordsed maksed Summa;Valuuta
        EE3600109435;0,80;150000000,000;800,00;0;0;EUR
        """;

    assertThatThrownBy(() -> parser.parse(csv)).isInstanceOf(IllegalArgumentException.class);
  }
}
