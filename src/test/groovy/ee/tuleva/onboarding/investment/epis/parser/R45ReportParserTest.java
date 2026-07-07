package ee.tuleva.onboarding.investment.epis.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.epis.R45Result;
import ee.tuleva.onboarding.investment.epis.R45TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class R45ReportParserTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 12);

  private final R45ReportParser parser = new R45ReportParser(new EpisCsvParser());

  @Test
  void aggregatesInflowsAndOutflowsPerFundUsingSumma() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        ;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;1500,00;15.06.2026
        RED;EE3600109435;0,80000;0;500,00;15.06.2026
        SUB;EE3600109443;0,70000;0;200,00;15.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertThat(result.fundResults()).containsOnlyKeys("TUK75", "TUK00");
    assertResult(result.fundResults().get("TUK75"), "1500.00", "500.00", "1000.00");
    assertResult(result.fundResults().get("TUK00"), "200.00", "0", "200.00");
    assertThat(result.unvaluedRows()).isEmpty();
  }

  @Test
  void usesAbsoluteUnitsTimesRowNavWhenSummaIsZero() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        RED;EE3600109443;0,70000;-1000,000;0;15.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertResult(result.fundResults().get("TUK00"), "0", "700.00", "-700.00");
  }

  @Test
  void usesCallerSuppliedFallbackNavWhenRowNavIsZero() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        RED;EE3600109443;0;1000,000;0;15.06.2026
        """;

    R45ParseResult result =
        parser.parse(csv, TODAY, Map.of("EE3600109443", new BigDecimal("0.65")));

    assertResult(result.fundResults().get("TUK00"), "0", "650.00", "-650.00");
  }

  @Test
  void usesNavFromAnotherRowOfSameIsinWhenFallbackMissing() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109443;0,70000;0;200,00;15.06.2026
        RED;EE3600109443;0;1000,000;0;15.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertResult(result.fundResults().get("TUK00"), "200.00", "700.000", "-500.000");
  }

  @Test
  void recordsUnvaluedRowAndZeroFundEntryWhenNoNavAvailable() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        RED;EE3600109435;0;1000,000;0;15.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertResult(result.fundResults().get("TUK75"), "0", "0", "0");
    assertThat(result.unvaluedRows())
        .usingRecursiveFieldByFieldElementComparator(comparingBigDecimals())
        .containsExactly(
            new R45UnvaluedRow(
                "TUK75", R45TransactionType.RED, new BigDecimal("1000.000"), "EE3600109435"));
  }

  @Test
  void skipsSwsRowsWithZeroSummaAndZeroUnits() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SWS;EE3600109435;0,80000;0;0;15.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertThat(result.fundResults()).isEmpty();
    assertThat(result.unvaluedRows()).isEmpty();
  }

  @Test
  void skipsRowsWithSettlementDateBeforeToday() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;1500,00;11.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertThat(result.fundResults()).isEmpty();
  }

  @Test
  void keepsRowsWithSettlementDateTodayOrLater() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;1500,00;12.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertResult(result.fundResults().get("TUK75"), "1500.00", "0", "1500.00");
  }

  @Test
  void skipsRowsWithUnknownIsinOrUnknownTransactionType() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;XX0000000000;0,80000;0;1500,00;15.06.2026
        XXX;EE3600109435;0,80000;0;1500,00;15.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertThat(result.fundResults()).isEmpty();
  }

  @Test
  void throwsWhenSummaExceedsHundredMillion() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;150000000,00;15.06.2026
        """;

    assertThatThrownBy(() -> parser.parse(csv, TODAY, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenUnitsExceedHundredMillion() {
    String csv =
        """
        Tehtud: 12.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;150000000,000;0;15.06.2026
        """;

    assertThatThrownBy(() -> parser.parse(csv, TODAY, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenTehtudDateIsNotToday() {
    String csv =
        """
        Tehtud: 11.06.2026;;;;;
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;1500,00;15.06.2026
        """;

    assertThatThrownBy(() -> parser.parse(csv, TODAY, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwsWhenTehtudMarkerMissing() {
    String csv =
        """
        Tehingu liik;ISIN;NAV;Osakuid;Summa;Täitmise kuupäev
        SUB;EE3600109435;0,80000;0;1500,00;15.06.2026
        """;

    assertThatThrownBy(() -> parser.parse(csv, TODAY, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parsesEnglishFormatReport() {
    String csv =
        """
        Tehtud: 12.06.2026
        Tehingu liik,ISIN,NAV,Osakuid,Summa,Täitmise kuupäev
        SUB,EE3600109435,0.80000,0,1500.00,15.06.2026
        """;

    R45ParseResult result = parser.parse(csv, TODAY, Map.of());

    assertResult(result.fundResults().get("TUK75"), "1500.00", "0", "1500.00");
  }

  private static void assertResult(R45Result result, String inflow, String outflow, String net) {
    assertThat(result.inflowEur()).isEqualByComparingTo(inflow);
    assertThat(result.outflowEur()).isEqualByComparingTo(outflow);
    assertThat(result.netEur()).isEqualByComparingTo(net);
  }

  private static org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration
      comparingBigDecimals() {
    var configuration =
        new org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration();
    configuration.registerComparatorForType(BigDecimal::compareTo, BigDecimal.class);
    return configuration;
  }
}
