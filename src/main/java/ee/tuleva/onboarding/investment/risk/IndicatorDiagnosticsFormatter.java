package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;

import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorOutcome;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class IndicatorDiagnosticsFormatter {

  private static final int FULL_SRRI_WINDOW_OBSERVATIONS = 260;

  List<String> withDiagnostics(List<String> block, RiskIndicatorOutcome outcome) {
    dataQualityLine(outcome.indicator()).ifPresent(block::add);
    truncatedHistoryLine(outcome.indicator()).ifPresent(block::add);
    driftLine(outcome).ifPresent(block::add);
    redefinitionLine(outcome).ifPresent(block::add);
    skippedLine(outcome).ifPresent(block::add);
    return block;
  }

  private Optional<String> truncatedHistoryLine(PublishedRiskIndicator indicator) {
    if (!indicator.publishedSinceIsTruncated()) {
      return Optional.empty();
    }
    return Optional.of(
        "⚠️ %s %s: jooks algab salvestatud seeria esimesest punktist (%s), seega \"kehtib alates\""
                .formatted(indicator.fund(), indicator.indicatorType(), indicator.publishedSince())
            + " on alampiir, mitte tegelik algus — jooksuta RiskIndicatorBackfillJob.");
  }

  private Optional<String> driftLine(RiskIndicatorOutcome outcome) {
    var drifted = outcome.driftedDates();
    if (drifted.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "⚠️ %s %s: %d varasemat referentspunkti arvutati ümber (vanim %s, viimane %s) —"
                .formatted(
                    outcome.indicator().fund(),
                    outcome.indicator().indicatorType(),
                    drifted.size(),
                    drifted.getFirst(),
                    drifted.getLast())
            + " allikaandmed muutusid tagantjärele.");
  }

  private Optional<String> redefinitionLine(RiskIndicatorOutcome outcome) {
    var redefinitions = outcome.redefinitions();
    if (redefinitions.isEmpty()) {
      return Optional.empty();
    }
    var first = redefinitions.getFirst();
    return Optional.of(
        "ℹ️ %s %s: %d varasemat referentspunkti arvutati ümber (vanim %s, viimane %s) —"
                .formatted(
                    outcome.indicator().fund(),
                    outcome.indicator().indicatorType(),
                    redefinitions.size(),
                    first.date(),
                    redefinitions.getLast().date())
            + " hoidmisperioodi kauplemispäevi %s → %s. Alusandmed ei muutunud."
                .formatted(
                    text(first.previousHoldingPeriodTradingDays()),
                    first.holdingPeriodTradingDays()));
  }

  private Optional<String> skippedLine(RiskIndicatorOutcome outcome) {
    var skipped = outcome.skippedDates();
    if (skipped.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "⚠️ %s %s: %d referentspunkti jäi arvutamata (vanim %s, viimane %s) — VEV ei tulnud"
                .formatted(
                    outcome.indicator().fund(),
                    outcome.indicator().indicatorType(),
                    skipped.size(),
                    skipped.getFirst(),
                    skipped.getLast())
            + " lõplik arv, seeriasse jäi auk. 👉 Tegevus: kontrolli nende kuupäevade alushindu.");
  }

  private Optional<String> dataQualityLine(PublishedRiskIndicator indicator) {
    if (indicator.indicatorType() == SRI
        || indicator.latestObservationCount() >= FULL_SRRI_WINDOW_OBSERVATIONS
        || indicator.latestObservationCount() == 0) {
      return Optional.empty();
    }
    return Optional.of(
        "⚠️ %s %s: 5a aknas %d/%d nädalatootlust — puuduv NAV tuleb index_values tabelis parandada."
            .formatted(
                indicator.fund(),
                indicator.indicatorType(),
                indicator.latestObservationCount(),
                FULL_SRRI_WINDOW_OBSERVATIONS));
  }

  private String text(@Nullable Object value) {
    return value == null ? "—" : String.valueOf(value);
  }
}
