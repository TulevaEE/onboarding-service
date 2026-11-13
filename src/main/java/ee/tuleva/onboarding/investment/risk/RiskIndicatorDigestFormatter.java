package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RiskIndicatorDigestFormatter {

  private static final Locale ESTONIAN = Locale.of("et", "EE");

  private final RiskIndicatorProperties properties;
  private final IndicatorDetailFormatter detailFormatter;

  String digest(RiskIndicatorRun run) {
    var message = new StringBuilder();
    message.append(
        "%s Riskiindikaatorite kuuülevaade — seisuga %s\n"
            .formatted(severityCoveringWholeDigest(run).icon(), asOfDate(run)));
    message.append(headline(run)).append("\n\n");
    message.append("```\n").append(table(run)).append("```\n");

    for (var outcome : run.outcomes()) {
      message.append("\n").append(detailFormatter.detailBlock(outcome)).append("\n");
    }

    if (!run.failures().isEmpty()) {
      message.append("\n⚠️ Osa fonde jäi hindamata:\n");
      run.failures().forEach(failure -> message.append("  • ").append(failure).append("\n"));
    }

    message.append("\n").append(footer(run));
    return message.toString();
  }

  private String headline(RiskIndicatorRun run) {
    var counts = new EnumMap<Severity, Integer>(Severity.class);
    for (var outcome : run.outcomes()) {
      var severity =
          detailFormatter.severity(
              outcome.indicator(), detailFormatter.disclosedClass(outcome.indicator()));
      counts.merge(severity, 1, Integer::sum);
    }

    var parts = new ArrayList<String>();
    if (counts.getOrDefault(Severity.RED, 0) > 0) {
      parts.add("%d vajab tegevust".formatted(counts.get(Severity.RED)));
    }
    if (counts.getOrDefault(Severity.YELLOW, 0) > 0) {
      parts.add("%d jälgimist".formatted(counts.get(Severity.YELLOW)));
    }
    if (counts.getOrDefault(Severity.GREEN, 0) > 0) {
      parts.add("%d korras".formatted(counts.get(Severity.GREEN)));
    }
    if (!run.failures().isEmpty()) {
      parts.add("%d hindamata".formatted(run.failures().size()));
    }
    return parts.isEmpty() ? "Ühtegi fondi ei hinnatud." : String.join(", ", parts) + ".";
  }

  private Severity severityCoveringWholeDigest(RiskIndicatorRun run) {
    var worstFund = worstSeverityAcrossFunds(run);
    if (worstFund == Severity.RED) {
      return Severity.RED;
    }
    return run.failures().isEmpty() ? worstFund : Severity.YELLOW;
  }

  private Severity worstSeverityAcrossFunds(RiskIndicatorRun run) {
    var worst = Severity.GREEN;
    for (var outcome : run.outcomes()) {
      var severity =
          detailFormatter.severity(
              outcome.indicator(), detailFormatter.disclosedClass(outcome.indicator()));
      if (severity.compareTo(worst) > 0) {
        worst = severity;
      }
    }
    return worst;
  }

  private String asOfDate(RiskIndicatorRun run) {
    return run.outcomes().stream()
        .map(outcome -> outcome.indicator().evaluationDate())
        .max(LocalDate::compareTo)
        .map(LocalDate::toString)
        .orElse(run.runDate().toString());
  }

  private String table(RiskIndicatorRun run) {
    var format = "%-7s %-7s %-10s %-10s %-14s %-8s %-8s %s%n";
    var table = new StringBuilder();
    table.append(
        String.format(
            format,
            "Fond",
            "Näitaja",
            "Arvutatud",
            "Avaldatud",
            "Kehtib alates",
            "Kestus",
            "Eelmine",
            "Staatus"));
    for (var outcome : run.outcomes()) {
      var indicator = outcome.indicator();
      var row = detailFormatter.tableRow(indicator);
      table.append(
          String.format(
              format,
              indicator.fund(),
              indicator.indicatorType(),
              text(indicator.publishedClass()),
              row.disclosedClassColumn(),
              row.publishedSince(),
              row.duration(),
              text(indicator.previousPublishedClass()),
              row.statusLabel()));
    }
    return table.toString();
  }

  private String footer(RiskIndicatorRun run) {
    var sources = new ArrayList<String>();
    for (var outcome : run.outcomes()) {
      var sourceKeys = new ArrayList<String>();
      for (var source : properties.sourcesFor(outcome.indicator().fund())) {
        sourceKeys.add(source.key());
      }
      sources.add("%s: %s".formatted(outcome.indicator().fund(), String.join("+", sourceKeys)));
    }
    return "Allikad: %s. SRI = MRM, eeldusel CRM = %d.%n"
        .formatted(
            String.join("; ", sources),
            SriCalculator.ASSUMED_CRM_KEEPING_SRI_AT_MRM_PER_PRIIPS_ANNEX_II_P52);
  }

  private String text(@Nullable Object value) {
    return value == null ? "—" : String.valueOf(value);
  }
}
