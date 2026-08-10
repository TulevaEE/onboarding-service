package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorOutcome;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class RiskIndicatorNotifier {

  private static final int DIGEST_BUSINESS_DAY = 4;
  private static final int PERSISTENCE_WINDOW_MONTHS = 4;
  private static final int FULL_SRRI_WINDOW_OBSERVATIONS = 260;
  private static final Locale ESTONIAN = Locale.of("et", "EE");

  private final ee.tuleva.onboarding.notification.OperationsNotificationService notificationService;
  private final DisclosedRiskIndicatorRepository disclosureRepository;
  private final RiskIndicatorDigestRepository digestRepository;
  private final RiskIndicatorProperties properties;
  private final FundValueRepository fundValueRepository;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  void notify(RiskIndicatorRun run) {
    try {
      notifyTransitions(run);
    } catch (Exception e) {
      log.error("Failed to send risk indicator transition notification", e);
    }
    try {
      sendDigestIfDue(run);
    } catch (Exception e) {
      log.error("Failed to send risk indicator digest", e);
    }
  }

  // --- immediate alerts -------------------------------------------------------------------

  private void notifyTransitions(RiskIndicatorRun run) {
    var lines = new ArrayList<String>();
    for (var outcome : run.outcomes()) {
      lines.addAll(transitionLines(outcome));
    }
    if (lines.isEmpty()) {
      return;
    }
    notificationService.sendMessage(
        "Riskiindikaatori muutus\n" + String.join("\n", lines), INVESTMENT);
  }

  private List<String> transitionLines(RiskIndicatorOutcome outcome) {
    var indicator = outcome.indicator();
    var previous = outcome.previous();
    var disclosed = disclosedClass(indicator);
    var lines = new ArrayList<String>();

    // A status transition is only defined against a previous run. On a cold start the publication
    // table is empty, so there is nothing to transition from and the alert stays silent.
    if (previous != null
        && !Objects.equals(previous.publishedClass(), indicator.publishedClass())) {
      lines.add(
          "🔔 %s %s — avaldatav klass muutus %s → %s (kehtib alates %s)"
              .formatted(
                  indicator.fund(),
                  indicator.indicatorType(),
                  previous.publishedClass(),
                  indicator.publishedClass(),
                  indicator.publishedSince()));
    } else if (previous != null && previous.status() != indicator.status()) {
      lines.add(
          "%s %s %s — staatus %s → %s (arvutatud klass %s, avaldatav klass %s)"
              .formatted(
                  statusEmoji(indicator.status()),
                  indicator.fund(),
                  indicator.indicatorType(),
                  previous.status(),
                  indicator.status(),
                  indicator.rawLatestClass(),
                  indicator.publishedClass()));
    }

    // A document that disagrees with the computed class is a compliance defect, not a transition:
    // it is just as true on the very first run, so it is not suppressed on a cold start.
    var mismatched = isMismatched(disclosed, indicator);
    var wasMismatched =
        previous != null
            && disclosed != null
            && !Objects.equals(previous.publishedClass(), disclosed.getDisclosedClass());
    if (mismatched && !wasMismatched) {
      lines.add(mismatchLine(indicator, disclosed));
    }
    return lines;
  }

  // --- monthly digest ---------------------------------------------------------------------

  private void sendDigestIfDue(RiskIndicatorRun run) {
    var today = LocalDate.now(clock);
    var month = today.withDayOfMonth(1);
    if (!publicHolidays.isOnOrAfterNthBusinessDayOfMonth(today, DIGEST_BUSINESS_DAY)) {
      return;
    }
    if (digestRepository.existsByDigestMonth(month)) {
      return;
    }
    notificationService.sendMessage(digest(run), INVESTMENT);
    digestRepository.save(RiskIndicatorDigest.builder().digestMonth(month).build());
  }

  private String digest(RiskIndicatorRun run) {
    var message = new StringBuilder();
    message.append("📊 Riskiindikaatorite kuuülevaade — seisuga %s\n\n".formatted(asOfDate(run)));
    message.append("```\n").append(table(run)).append("```\n");

    for (var outcome : run.outcomes()) {
      message.append("\n").append(detailBlock(outcome)).append("\n");
    }

    proxyReviewLines(run).forEach(line -> message.append("\n").append(line).append("\n"));

    if (!run.failures().isEmpty()) {
      message.append("\n⚠️ Osa fonde jäi hindamata:\n");
      run.failures().forEach(failure -> message.append("  • ").append(failure).append("\n"));
    }

    message.append("\n").append(footer(run));
    return message.toString();
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
      var disclosed = disclosedClass(indicator);
      table.append(
          String.format(
              format,
              indicator.fund(),
              indicator.indicatorType(),
              text(indicator.publishedClass()),
              disclosed == null ? "?" : String.valueOf(disclosed.getDisclosedClass()),
              text(indicator.publishedSince()),
              duration(indicator),
              text(indicator.previousPublishedClass()),
              statusLabel(indicator, disclosed)));
    }
    return table.toString();
  }

  private String detailBlock(RiskIndicatorOutcome outcome) {
    var indicator = outcome.indicator();
    var disclosed = disclosedClass(indicator);
    var block = new ArrayList<String>();

    if (!indicator.hasClass()) {
      block.add("❔ %s %s — andmeid napib".formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Aknas on %d vaatlust, klassi ei avaldata. Volatiilsus %s."
              .formatted(indicator.latestObservationCount(), volatility(indicator)));
      block.add("👉 Tegevus: kontrolli, kas NAV-seeria on täielik — fondijuht.");
      return String.join("\n", block);
    }

    if (isMismatched(disclosed, indicator)) {
      block.add(mismatchLine(indicator, disclosed));
      block.add(
          "Muutus jõustus %s. Viimane dokument: '%s' (klass %d, alates %s)."
              .formatted(
                  indicator.publishedSince(),
                  Objects.requireNonNull(disclosed).getDocument(),
                  disclosed.getDisclosedClass(),
                  disclosed.getDisclosedFrom()));
      block.add(
          "👉 Tegevus: dokument vajab uuendamist — riskijuht. Pärast avaldamist lisa rida"
              + " investment_risk_indicator_disclosure tabelisse.");
    } else if (disclosed == null) {
      block.add(
          "❔ %s %s — avaldatud klass teadmata"
              .formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Arvutatud avaldatav klass on %d, aga ühtegi dokumendirida ei ole."
              .formatted(indicator.publishedClass()));
      block.add(
          "👉 Tegevus: lisa kehtiv KID/KIID rida investment_risk_indicator_disclosure tabelisse"
              + " — riskijuht.");
    } else if (indicator.status() == RiskIndicatorStatus.CHANGE_PENDING) {
      block.add("⚠️ %s %s — muutus ootel".formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Toores klass %s, avaldatav klass %d. %s %s."
              .formatted(
                  indicator.rawLatestClass(),
                  indicator.publishedClass(),
                  volatilityLabel(indicator),
                  volatility(indicator)));
      block.add(pendingProgress(indicator));
      block.add("👉 Tegevus praegu pole — jälgi.");
    } else if (indicator.status() == RiskIndicatorStatus.CHANGE_CONFIRMED) {
      block.add(
          "🔔 %s %s — muutus äsja kinnitatud"
              .formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Avaldatav klass %d alates %s (eelmine %s). %s"
              .formatted(
                  indicator.publishedClass(),
                  indicator.publishedSince(),
                  text(indicator.previousPublishedClass()),
                  rangeLine(indicator)));
      block.add("👉 Tegevus: kontrolli, kas dokument on juba uuendatud — riskijuht.");
    } else {
      block.add(
          "✅ %s %s — stabiilne, dokument ajakohane"
              .formatted(indicator.fund(), indicator.indicatorType()));
      block.add(rangeLine(indicator));
      block.add(majorityLine(indicator));
    }

    dataQualityLine(indicator).ifPresent(block::add);
    return String.join("\n", block);
  }

  private String mismatchLine(
      PublishedRiskIndicator indicator, @Nullable DisclosedRiskIndicator disclosed) {
    return "🔴 %s %s — dokumendis on klass %s, arvutatud avaldatav klass on %s"
        .formatted(
            indicator.fund(),
            indicator.indicatorType(),
            disclosed == null ? "?" : disclosed.getDisclosedClass(),
            indicator.publishedClass());
  }

  private String rangeLine(PublishedRiskIndicator indicator) {
    var publishedClass = Objects.requireNonNull(indicator.publishedClass());
    var range = RiskClassBucket.range(indicator.indicatorType(), publishedClass);
    var volatility = indicator.latestVolatility();
    if (volatility == null) {
      return "Klassi %d vahemik %s.".formatted(publishedClass, rangeText(indicator, range));
    }
    var distance =
        RiskClassBucket.distanceToNearestBound(
            indicator.indicatorType(), publishedClass, volatility);
    return "%s %s (klassi %d vahemik %s); lähim piir on %s kaugusel."
        .formatted(
            volatilityLabel(indicator),
            volatility(indicator),
            publishedClass,
            rangeText(indicator, range),
            distance == null ? "—" : number(distance, indicator));
  }

  private String rangeText(PublishedRiskIndicator indicator, RiskClassBucket.ClassRange range) {
    var lower = range.lowerInclusive();
    var upper = range.upperExclusive();
    return "%s–%s"
        .formatted(
            lower == null ? "0" : number(lower, indicator),
            upper == null ? "∞" : number(upper, indicator));
  }

  private String majorityLine(PublishedRiskIndicator indicator) {
    if (indicator.indicatorType() == SRI) {
      var needed = indicator.windowReferencePoints() / 2 + 1;
      return "PRIIPs 4-kuu enamus: %d/%d referentspunkti klassile %s; pöördeks on vaja %d"
              .formatted(
                  indicator.matchingReferencePoints(),
                  indicator.windowReferencePoints(),
                  text(indicator.rawLatestClass()),
                  needed)
          + " vastupidist referentspunkti.";
    }
    return "CESR 4-kuu aken: %d/%d nädalat klassile %s; migratsiooniks peab volatiilsus olema kõik"
            .formatted(
                indicator.matchingReferencePoints(),
                indicator.windowReferencePoints(),
                text(indicator.rawLatestClass()))
        + " neli kuud väljaspool avaldatavat klassi.";
  }

  private String pendingProgress(PublishedRiskIndicator indicator) {
    var since = indicator.rawClassSince();
    if (indicator.indicatorType() == SRI) {
      var needed = indicator.windowReferencePoints() / 2 + 1;
      var missing = Math.max(0, needed - indicator.matchingReferencePoints());
      return "Klass %s on hoidnud %d kauplemispäeva alates %s. PRIIPs enamuseni puudu %d punkti."
          .formatted(
              text(indicator.rawLatestClass()),
              indicator.rawStreakReferencePoints(),
              text(since),
              missing);
    }
    if (since == null) {
      return "Klass %s on hoidnud %d nädalat."
          .formatted(text(indicator.rawLatestClass()), indicator.rawStreakReferencePoints());
    }
    var threshold = since.plusMonths(PERSISTENCE_WINDOW_MONTHS);
    var weeksLeft =
        Math.max(0, ChronoUnit.WEEKS.between(indicator.evaluationDate(), threshold) + 1);
    return "Klass %s on püsinud %d nädalat alates %s. CESR 4-kuu künniseni puudu %d nädalat,"
            .formatted(
                text(indicator.rawLatestClass()),
                indicator.rawStreakReferencePoints(),
                since,
                weeksLeft)
        + " eeldatav kinnitus %s.".formatted(threshold);
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

  // --- proxy review -----------------------------------------------------------------------

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
      var earliest = fundValueRepository.findEarliestDateForKey(review.ownHistoryKey());
      if (earliest.isEmpty()) {
        continue;
      }
      var years =
          ChronoUnit.DAYS.between(earliest.get(), outcome.indicator().evaluationDate()) / 365.25;
      if (years < review.requiredYears()) {
        continue;
      }
      lines.add(
          ("🔔 %s %s — võrdlusindeksi proxy vajab ülevaatust\n"
                  + "Fondil on nüüd %s aastat oma NAV-ajalugu (alates %s); indikaator arvutatakse"
                  + " endiselt allikast %s. Annex II p5 lävend on täidetud.\n"
                  + "👉 Tegevus: riskijuht — otsusta, kas minna üle oma andmetele.")
              .formatted(
                  fund,
                  outcome.indicator().indicatorType(),
                  String.format(ESTONIAN, "%.1f", years),
                  earliest.get(),
                  activeSourceKey));
    }
    return lines;
  }

  // --- formatting -------------------------------------------------------------------------

  private String footer(RiskIndicatorRun run) {
    var sources =
        run.outcomes().stream()
            .map(
                outcome ->
                    "%s: %s"
                        .formatted(
                            outcome.indicator().fund(),
                            String.join(
                                "+",
                                properties.sourcesFor(outcome.indicator().fund()).stream()
                                    .map(RiskIndicatorProperties.Source::key)
                                    .toList())))
            .toList();
    return "Allikad: %s. SRI = MRM, eeldusel CRM = %d.%n"
        .formatted(String.join("; ", sources), SriCalculator.ASSUMED_CREDIT_RISK_MEASURE);
  }

  private @Nullable DisclosedRiskIndicator disclosedClass(PublishedRiskIndicator indicator) {
    return disclosureRepository
        .findFirstByIndicatorTypeAndFundAndDisclosedFromLessThanEqualOrderByDisclosedFromDesc(
            indicator.indicatorType(), indicator.fund(), indicator.evaluationDate())
        .orElse(null);
  }

  private boolean isMismatched(
      @Nullable DisclosedRiskIndicator disclosed, PublishedRiskIndicator indicator) {
    return disclosed != null
        && indicator.publishedClass() != null
        && !Objects.equals(disclosed.getDisclosedClass(), indicator.publishedClass());
  }

  private String statusLabel(
      PublishedRiskIndicator indicator, @Nullable DisclosedRiskIndicator disclosed) {
    if (!indicator.hasClass()) {
      return "❔ andmeid napib";
    }
    if (isMismatched(disclosed, indicator)) {
      return "🔴 DOKUMENT VANANENUD";
    }
    if (disclosed == null) {
      return "❔ avaldatud teadmata";
    }
    return switch (indicator.status()) {
      case STABLE -> "✅ stabiilne";
      case CHANGE_PENDING -> "⚠️ muutus ootel";
      case CHANGE_CONFIRMED -> "🔔 muutus kinnitatud";
    };
  }

  private String statusEmoji(RiskIndicatorStatus status) {
    return switch (status) {
      case STABLE -> "✅";
      case CHANGE_PENDING -> "⚠️";
      case CHANGE_CONFIRMED -> "🔔";
    };
  }

  private String duration(PublishedRiskIndicator indicator) {
    var since = indicator.publishedSince();
    if (since == null) {
      return "—";
    }
    var months = ChronoUnit.MONTHS.between(since, indicator.evaluationDate());
    return months < 12
        ? "%d kuud".formatted(months)
        : "%da %dk".formatted(months / 12, months % 12);
  }

  private String volatilityLabel(PublishedRiskIndicator indicator) {
    return indicator.indicatorType() == SRI ? "VEV" : "Aastane volatiilsus";
  }

  private String volatility(PublishedRiskIndicator indicator) {
    var volatility = indicator.latestVolatility();
    return volatility == null ? "—" : number(volatility, indicator);
  }

  /** SRI is read as a VEV in decimals, SRRI as an annualised volatility in per cent. */
  private String number(BigDecimal value, PublishedRiskIndicator indicator) {
    if (indicator.indicatorType() == SRI) {
      return String.format(ESTONIAN, "%.4f", value);
    }
    return String.format(
            ESTONIAN,
            "%.2f",
            value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
        + "%";
  }

  private String text(@Nullable Object value) {
    return value == null ? "—" : String.valueOf(value);
  }
}
