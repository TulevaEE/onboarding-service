package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorStatus.STABLE;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRRI;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicatorDiagnosticsFormatterTest {

  private static final LocalDate EVALUATION_DATE = LocalDate.of(2026, 7, 31);

  private final IndicatorDiagnosticsFormatter diagnosticsFormatter =
      new IndicatorDiagnosticsFormatter();

  @Test
  void aTruncatedPublishedSinceIsExplainedAsALowerBound() {
    var indicator =
        new PublishedRiskIndicator(
            TKF100,
            SRI,
            EVALUATION_DATE,
            4,
            4,
            null,
            LocalDate.of(2026, 3, 14),
            true,
            LocalDate.of(2026, 3, 14),
            85,
            85,
            85,
            85,
            1305,
            new BigDecimal("0.154000000000"),
            STABLE);

    var lines = diagnosticsFormatter.withDiagnostics(new ArrayList<>(), outcome(indicator));

    assertThat(lines)
        .containsExactly(
            "⚠️ TKF100 SRI: jooks algab salvestatud seeria esimesest punktist (2026-03-14), seega"
                + " \"kehtib alates\" on alampiir, mitte tegelik algus — jooksuta"
                + " RiskIndicatorBackfillJob.");
  }

  @Test
  void aShortSrriWindowRaisesADataQualityLine() {
    var indicator =
        new PublishedRiskIndicator(
            TUK75,
            SRRI,
            EVALUATION_DATE,
            4,
            4,
            null,
            LocalDate.of(2021, 9, 6),
            false,
            LocalDate.of(2021, 9, 6),
            250,
            250,
            17,
            17,
            259,
            new BigDecimal("0.0930"),
            STABLE);

    var lines = diagnosticsFormatter.withDiagnostics(new ArrayList<>(), outcome(indicator));

    assertThat(lines)
        .containsExactly(
            "⚠️ TUK75 SRRI: 5a aknas 259/260 nädalatootlust — puuduv NAV tuleb index_values"
                + " tabelis parandada.");
  }

  @Test
  void aFullSrriWindowRaisesNoDataQualityLine() {
    var indicator = stableSri();

    var lines = diagnosticsFormatter.withDiagnostics(new ArrayList<>(), outcome(indicator));

    assertThat(lines).isEmpty();
  }

  @Test
  void recomputedHistoricalPointsAreCalledOut() {
    var outcome =
        new RiskIndicatorService.RiskIndicatorOutcome(
            stableSri(),
            null,
            RiskIndicatorPublication.builder().build(),
            List.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
            List.of(),
            List.of());

    var lines = diagnosticsFormatter.withDiagnostics(new ArrayList<>(), outcome);

    assertThat(lines)
        .containsExactly(
            "⚠️ TKF100 SRI: 2 varasemat referentspunkti arvutati ümber (vanim 2026-06-01, viimane"
                + " 2026-06-30) — allikaandmed muutusid tagantjärele.");
  }

  @Test
  void aChangedHoldingPeriodIsReportedAsARedefinitionRatherThanSourceDataDrift() {
    var outcome =
        new RiskIndicatorService.RiskIndicatorOutcome(
            stableSri(),
            null,
            RiskIndicatorPublication.builder().build(),
            List.of(),
            List.of(
                new Redefinition(LocalDate.of(2026, 6, 1), "1300", "1280"),
                new Redefinition(LocalDate.of(2026, 6, 30), "1300", "1280")),
            List.of());

    var lines = diagnosticsFormatter.withDiagnostics(new ArrayList<>(), outcome);

    assertThat(lines)
        .containsExactly(
            "ℹ️ TKF100 SRI: 2 varasemat referentspunkti arvutati ümber (vanim 2026-06-01, viimane"
                + " 2026-06-30) — hoidmisperioodi kauplemispäevi 1300 → 1280. Alusandmed ei"
                + " muutunud.");
  }

  @Test
  void referencePointsTheCalculatorCouldNotUseAreCalledOut() {
    var outcome =
        new RiskIndicatorService.RiskIndicatorOutcome(
            stableSri(),
            null,
            RiskIndicatorPublication.builder().build(),
            List.of(),
            List.of(),
            List.of(LocalDate.of(2026, 6, 2), LocalDate.of(2026, 7, 1)));

    var lines = diagnosticsFormatter.withDiagnostics(new ArrayList<>(), outcome);

    assertThat(lines)
        .containsExactly(
            "⚠️ TKF100 SRI: 2 referentspunkti jäi arvutamata (vanim 2026-06-02, viimane"
                + " 2026-07-01) — VEV ei tulnud lõplik arv, seeriasse jäi auk. 👉 Tegevus:"
                + " kontrolli nende kuupäevade alushindu.");
  }

  private PublishedRiskIndicator stableSri() {
    return new PublishedRiskIndicator(
        TKF100,
        SRI,
        EVALUATION_DATE,
        4,
        4,
        null,
        LocalDate.of(2026, 3, 14),
        false,
        LocalDate.of(2026, 3, 14),
        85,
        85,
        85,
        85,
        1305,
        new BigDecimal("0.154000000000"),
        STABLE);
  }

  private RiskIndicatorService.RiskIndicatorOutcome outcome(PublishedRiskIndicator indicator) {
    return new RiskIndicatorService.RiskIndicatorOutcome(
        indicator,
        null,
        RiskIndicatorPublication.builder().build(),
        List.of(),
        List.of(),
        List.of());
  }
}
