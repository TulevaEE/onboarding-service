package ee.tuleva.onboarding.investment.risk;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RiskIndicatorDigestFormatter {

  private static final Locale ESTONIAN = Locale.of("et", "EE");

  private final RiskIndicatorProperties properties;
  private final FundValueQueries fundValueQueries;
  private final IndicatorDetailFormatter detailFormatter;

  String digest(RiskIndicatorRun run) {
    var proxyReviews = proxyReviewLines(run);
    var message = new StringBuilder();
    message.append(
        "%s Riskiindikaatorite kuuülevaade — seisuga %s\n"
            .formatted(severityCoveringWholeDigest(run, proxyReviews).icon(), asOfDate(run)));
    message.append(headline(run, proxyReviews)).append("\n\n");
    message.append("```\n").append(table(run)).append("```\n");

    for (var outcome : run.outcomes()) {
      message.append("\n").append(detailFormatter.detailBlock(outcome)).append("\n");
    }

    proxyReviews.forEach(line -> message.append("\n").append(line).append("\n"));

    if (!run.failures().isEmpty()) {
      message.append("\n⚠️ Osa fonde jäi hindamata:\n");
      run.failures().forEach(failure -> message.append("  • ").append(failure).append("\n"));
    }

    message.append("\n").append(footer(run));
    return message.toString();
  }

  private String headline(RiskIndicatorRun run, List<String> proxyReviews) {
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
    if (!proxyReviews.isEmpty()) {
      parts.add("%d proxy ülevaatust".formatted(proxyReviews.size()));
    }
    return parts.isEmpty() ? "Ühtegi fondi ei hinnatud." : String.join(", ", parts) + ".";
  }

  private Severity severityCoveringWholeDigest(RiskIndicatorRun run, List<String> proxyReviews) {
    var worstFund = worstSeverityAcrossFunds(run);
    if (worstFund == Severity.RED) {
      return Severity.RED;
    }
    return hasLinesTiedToNoFund(run, proxyReviews) ? Severity.YELLOW : worstFund;
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

  private boolean hasLinesTiedToNoFund(RiskIndicatorRun run, List<String> proxyReviews) {
    return !run.failures().isEmpty() || !proxyReviews.isEmpty();
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

  private List<String> proxyReviewLines(RiskIndicatorRun run) {
    var lines = new ArrayList<String>();
    for (var outcome : run.outcomes()) {
      var fund = outcome.indicator().fund();
      var review = properties.proxyReviewFor(fund);
      if (review == null) {
        continue;
      }
      var activeSourceKey = properties.sourcesFor(fund).getLast().key();
      if (activeSourceKey.equals(review.ownHistoryKey())) {
        continue;
      }
      var earliest = fundValueQueries.findEarliestDateForKey(review.ownHistoryKey());
      if (earliest.isEmpty()) {
        continue;
      }
      var years =
          ChronoUnit.DAYS.between(earliest.get(), outcome.indicator().evaluationDate()) / 365.25;
      if (years < review.requiredYears()) {
        continue;
      }
      lines.add(
          ("⚠️ %s %s — võrdlusindeksi proxy vajab ülevaatust\n"
                  + "Fondil on nüüd %s aastat oma NAV-ajalugu (alates %s); indikaator arvutatakse"
                  + " endiselt allikast %s. Annex II p5 lävend on täidetud.\n"
                  + "👉 Tegevus: otsusta, kas minna üle oma andmetele.")
              .formatted(
                  fund,
                  outcome.indicator().indicatorType(),
                  String.format(ESTONIAN, "%.1f", years),
                  earliest.get(),
                  activeSourceKey));
    }
    return lines;
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
        .formatted(String.join("; ", sources), SriCalculator.ASSUMED_CREDIT_RISK_MEASURE);
  }

  private String text(@Nullable Object value) {
    return value == null ? "—" : String.valueOf(value);
  }
}
