package ee.tuleva.onboarding.investment.epis.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.epis.R21Result;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

class R21ReportParserTest {

  private static final YearMonth EXECUTION_MONTH = YearMonth.of(2026, 9);

  private final R21ReportParser parser = new R21ReportParser(new EpisCsvParser());

  @Test
  void aggregatesRavaUnitsPerFund() {
    String csv =
        """
        Maksete kuu: 202609;;;;
        ;;;;
        Väärtpaber;Jooksev NAV;Osakud;Summa;Valuuta
        Tuleva Maailma Aktsiate Pensionifond;0,80;100,000;80,00;EUR
        Tuleva Maailma Aktsiate Pensionifond;0,80;50,000;40,00;EUR
        Tuleva Maailma Võlakirjade Pensionifond;0,70;-200,000;140,00;EUR
        """;

    Map<String, R21Result> result = parser.parse(csv, EXECUTION_MONTH);

    assertThat(result).containsOnlyKeys("TUK75", "TUK00");
    assertThat(result.get("TUK75").ravaUnits()).isEqualByComparingTo("150.000");
    assertThat(result.get("TUK00").ravaUnits()).isEqualByComparingTo("200.000");
  }

  @Test
  void throwsWhenMakseteKuuDoesNotMatchExpectedExecutionMonth() {
    String csv =
        """
        Maksete kuu: 202605;;;;
        Väärtpaber;Jooksev NAV;Osakud;Summa;Valuuta
        Tuleva Maailma Aktsiate Pensionifond;0,80;100,000;80,00;EUR
        """;

    assertThatThrownBy(() -> parser.parse(csv, EXECUTION_MONTH))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenMakseteKuuMissing() {
    String csv =
        """
        Väärtpaber;Jooksev NAV;Osakud;Summa;Valuuta
        Tuleva Maailma Aktsiate Pensionifond;0,80;100,000;80,00;EUR
        """;

    assertThatThrownBy(() -> parser.parse(csv, EXECUTION_MONTH))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void skipsRowsWithZeroUnitsOrUnknownFund() {
    String csv =
        """
        Maksete kuu: 202609;;;;
        Väärtpaber;Jooksev NAV;Osakud;Summa;Valuuta
        Tuleva Maailma Aktsiate Pensionifond;0,80;0;0;EUR
        Mingi Muu Fond;0,80;100,000;80,00;EUR
        """;

    Map<String, R21Result> result = parser.parse(csv, EXECUTION_MONTH);

    assertThat(result).isEmpty();
  }

  @Test
  void throwsWhenUnitsExceedHundredMillion() {
    String csv =
        """
        Maksete kuu: 202609;;;;
        Väärtpaber;Jooksev NAV;Osakud;Summa;Valuuta
        Tuleva Maailma Aktsiate Pensionifond;0,80;150000000,000;80,00;EUR
        """;

    assertThatThrownBy(() -> parser.parse(csv, EXECUTION_MONTH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
