package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.deadline.BusinessDays;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.PublicationSnapshot;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorOutcome;
import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorRun;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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

  private final OperationsNotificationService notificationService;
  private final DisclosedRiskIndicatorRepository disclosureRepository;
  private final RiskIndicatorDigestRepository digestRepository;
  private final RiskIndicatorPublicationRepository publicationRepository;
  private final RiskIndicatorProperties properties;
  private final FundValueQueries fundValueQueries;
  private final BusinessDays businessDays;
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

  private void notifyTransitions(RiskIndicatorRun run) {
    var lines = new ArrayList<String>();
    for (var outcome : run.outcomes()) {
      lines.addAll(transitionLines(outcome));
    }
    if (!lines.isEmpty()) {
      notificationService.sendMessage(
          "Riskiindikaatori muutus\n" + String.join("\n", lines), INVESTMENT);
    }
    becomeBaselineForNextComparison(run);
  }

  private void becomeBaselineForNextComparison(RiskIndicatorRun run) {
    var publications =
        run.outcomes().stream()
            .map(
                outcome -> {
                  var publication = outcome.publication();
                  var disclosed = disclosedClass(outcome.indicator());
                  publication.setNotified(true);
                  publication.setNotifiedDisclosedClass(
                      disclosed == null ? null : disclosed.getDisclosedClass());
                  return publication;
                })
            .toList();
    publicationRepository.saveAll(publications);
  }

  private List<String> transitionLines(RiskIndicatorOutcome outcome) {
    var indicator = outcome.indicator();
    var previous = outcome.previous();
    var disclosed = disclosedClass(indicator);
    var lines = new ArrayList<String>();

    if (previous != null && hasPublishedClassChangedSinceLastMessage(previous, indicator)) {
      lines.add(
          "⚠️ %s %s — avaldatav klass muutus %s → %s (kehtib alates %s)"
              .formatted(
                  indicator.fund(),
                  indicator.indicatorType(),
                  previous.publishedClass(),
                  indicator.publishedClass(),
                  indicator.publishedSince()));
    } else if (previous != null && hasStatusChangedSinceLastMessage(previous, indicator)) {
      lines.add(
          "%s %s %s — staatus %s → %s (arvutatud klass %s, avaldatav klass %s)"
              .formatted(
                  severity(indicator, disclosed).icon(),
                  indicator.fund(),
                  indicator.indicatorType(),
                  previous.status(),
                  indicator.status(),
                  indicator.rawLatestClass(),
                  indicator.publishedClass()));
    }

    if (isComplianceDefectRatherThanTransition(previous, disclosed, indicator)) {
      lines.add(mismatchLine(indicator, disclosed));
    }
    return lines;
  }

  private boolean isComplianceDefectRatherThanTransition(
      @Nullable PublicationSnapshot previous,
      @Nullable DisclosedRiskIndicator disclosed,
      PublishedRiskIndicator indicator) {
    return isMismatched(disclosed, indicator)
        && !isMismatchAlreadyReportedForSameDisclosedClass(previous, disclosed);
  }

  private boolean hasPublishedClassChangedSinceLastMessage(
      @Nullable PublicationSnapshot previous, PublishedRiskIndicator indicator) {
    return previous != null
        && !Objects.equals(previous.publishedClass(), indicator.publishedClass());
  }

  private boolean hasStatusChangedSinceLastMessage(
      @Nullable PublicationSnapshot previous, PublishedRiskIndicator indicator) {
    return previous != null && previous.status() != indicator.status();
  }

  private boolean isMismatchAlreadyReportedForSameDisclosedClass(
      @Nullable PublicationSnapshot previous, @Nullable DisclosedRiskIndicator disclosed) {
    return previous != null
        && disclosed != null
        && lastMessageReportedMismatchOfClass(previous, disclosed.getDisclosedClass());
  }

  private boolean lastMessageReportedMismatchOfClass(
      PublicationSnapshot previous, @Nullable Integer disclosedClass) {
    var reported = previous.notifiedDisclosedClass();
    return reported != null
        && !Objects.equals(previous.publishedClass(), reported)
        && Objects.equals(reported, disclosedClass);
  }

  private void sendDigestIfDue(RiskIndicatorRun run) {
    var today = LocalDate.now(clock);
    var month = today.withDayOfMonth(1);
    if (!businessDays.isOnOrAfterNthBusinessDayOfMonth(today, DIGEST_BUSINESS_DAY)) {
      return;
    }

    var complete = run.failures().isEmpty();
    var existing = digestRepository.findByDigestMonth(month).orElse(null);
    if (existing != null && (existing.getComplete() || !complete)) {
      return;
    }

    var claim = claimMonthBeforeSending(existing, month, complete);
    try {
      notificationService.sendMessage(digest(run), INVESTMENT);
    } catch (RuntimeException e) {
      releaseClaimOnFailedSend(claim, existing);
      throw e;
    }
  }

  private RiskIndicatorDigest claimMonthBeforeSending(
      @Nullable RiskIndicatorDigest existing, LocalDate month, boolean complete) {
    if (existing == null) {
      return digestRepository.save(
          RiskIndicatorDigest.builder().digestMonth(month).complete(complete).build());
    }
    existing.setComplete(true);
    return digestRepository.save(existing);
  }

  private void releaseClaimOnFailedSend(
      RiskIndicatorDigest claim, @Nullable RiskIndicatorDigest existing) {
    if (existing == null) {
      digestRepository.delete(claim);
      return;
    }
    claim.setComplete(false);
    digestRepository.save(claim);
  }

  private String digest(RiskIndicatorRun run) {
    var proxyReviews = proxyReviewLines(run);
    var message = new StringBuilder();
    message.append(
        "%s Riskiindikaatorite kuuülevaade — seisuga %s\n"
            .formatted(severityCoveringWholeDigest(run, proxyReviews).icon(), asOfDate(run)));
    message.append(headline(run, proxyReviews)).append("\n\n");
    message.append("```\n").append(table(run)).append("```\n");

    for (var outcome : run.outcomes()) {
      message.append("\n").append(detailBlock(outcome)).append("\n");
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
    run.outcomes()
        .forEach(
            outcome ->
                counts.merge(
                    severity(outcome.indicator(), disclosedClass(outcome.indicator())),
                    1,
                    Integer::sum));

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
    return run.outcomes().stream()
        .map(outcome -> severity(outcome.indicator(), disclosedClass(outcome.indicator())))
        .max(Comparator.naturalOrder())
        .orElse(Severity.GREEN);
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
      var disclosed = disclosedClass(indicator);
      table.append(
          String.format(
              format,
              indicator.fund(),
              indicator.indicatorType(),
              text(indicator.publishedClass()),
              disclosed == null ? "?" : String.valueOf(disclosed.getDisclosedClass()),
              publishedSince(indicator),
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
      block.add("⚠️ %s %s — andmeid napib".formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Aknas on %d vaatlust, klassi ei avaldata. Volatiilsus %s."
              .formatted(indicator.latestObservationCount(), volatility(indicator)));
      block.add(
          "👉 Tegevus: kontrolli, kas NAV-seeria on täielik. Kui fondi enda ajalugu ongi nõutud"
              + " perioodist lühem, lisa investment.risk.sources alla võrdlusindeksi segment —"
              + " klassi avaldamata jätmine ei ole lubatud variant.");
      return String.join("\n", withDiagnostics(block, outcome));
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
          "👉 Tegevus: dokument vajab uuendamist. Pärast avaldamist lisa rida"
              + " investment_risk_indicator_disclosure tabelisse.");
    } else if (disclosed == null) {
      block.add(
          "⚠️ %s %s — avaldatud klass teadmata"
              .formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Arvutatud avaldatav klass on %d, aga ühtegi dokumendirida ei ole."
              .formatted(indicator.publishedClass()));
      block.add(
          "👉 Tegevus: lisa kehtiv KID/KIID rida investment_risk_indicator_disclosure"
              + " tabelisse.");
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
          "⚠️ %s %s — muutus äsja kinnitatud"
              .formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Avaldatav klass %d alates %s (eelmine %s). %s"
              .formatted(
                  indicator.publishedClass(),
                  indicator.publishedSince(),
                  text(indicator.previousPublishedClass()),
                  rangeLine(indicator)));
      block.add("👉 Tegevus: kontrolli, kas dokument on juba uuendatud.");
    } else {
      block.add(
          "✅ %s %s — stabiilne, dokument ajakohane"
              .formatted(indicator.fund(), indicator.indicatorType()));
      block.add(rangeLine(indicator));
      block.add(majorityLine(indicator));
    }

    return String.join("\n", withDiagnostics(block, outcome));
  }

  private List<String> withDiagnostics(List<String> block, RiskIndicatorOutcome outcome) {
    dataQualityLine(outcome.indicator()).ifPresent(block::add);
    truncatedHistoryLine(outcome.indicator()).ifPresent(block::add);
    driftLine(outcome).ifPresent(block::add);
    redefinitionLine(outcome).ifPresent(block::add);
    skippedLine(outcome).ifPresent(block::add);
    return block;
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
        + " eeldatav kinnitus %s; aknas on veel %d referentspunkti muus klassis."
            .formatted(threshold, referencePointsNotYetOnTheRawClass(indicator));
  }

  private int referencePointsNotYetOnTheRawClass(PublishedRiskIndicator indicator) {
    return indicator.windowReferencePoints() - indicator.matchingReferencePoints();
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
            indicator.indicatorType(), indicator.fund(), LocalDate.now(clock))
        .orElse(null);
  }

  private boolean isMismatched(
      @Nullable DisclosedRiskIndicator disclosed, PublishedRiskIndicator indicator) {
    return disclosed != null
        && indicator.publishedClass() != null
        && !Objects.equals(disclosed.getDisclosedClass(), indicator.publishedClass());
  }

  private enum Severity {
    GREEN("✅"),
    YELLOW("⚠️"),
    RED("🔴");

    private final String icon;

    Severity(String icon) {
      this.icon = icon;
    }

    String icon() {
      return icon;
    }
  }

  private Severity severity(
      PublishedRiskIndicator indicator, @Nullable DisclosedRiskIndicator disclosed) {
    if (isMismatched(disclosed, indicator)) {
      return Severity.RED;
    }
    if (!indicator.hasClass() || disclosed == null) {
      return Severity.YELLOW;
    }
    return indicator.status() == RiskIndicatorStatus.STABLE ? Severity.GREEN : Severity.YELLOW;
  }

  private String statusLabel(
      PublishedRiskIndicator indicator, @Nullable DisclosedRiskIndicator disclosed) {
    var icon = severity(indicator, disclosed).icon();
    if (!indicator.hasClass()) {
      return icon + " andmeid napib";
    }
    if (isMismatched(disclosed, indicator)) {
      return icon + " DOKUMENT VANANENUD";
    }
    if (disclosed == null) {
      return icon + " avaldatud teadmata";
    }
    return icon
        + switch (indicator.status()) {
          case STABLE -> " stabiilne";
          case CHANGE_PENDING -> " muutus ootel";
          case CHANGE_CONFIRMED -> " muutus kinnitatud";
        };
  }

  private String publishedSince(PublishedRiskIndicator indicator) {
    var since = indicator.publishedSince();
    if (since == null) {
      return "—";
    }
    return indicator.publishedSinceIsTruncated() ? "≥" + since : since.toString();
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

  private String number(BigDecimal value, PublishedRiskIndicator indicator) {
    return indicator.indicatorType() == SRI
        ? vevInDecimals(value)
        : annualisedVolatilityInPerCent(value);
  }

  private String vevInDecimals(BigDecimal value) {
    return String.format(ESTONIAN, "%.4f", value);
  }

  private String annualisedVolatilityInPerCent(BigDecimal value) {
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
