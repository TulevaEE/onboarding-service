package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.risk.RiskIndicatorType.SRI;

import ee.tuleva.onboarding.investment.risk.RiskIndicatorService.RiskIndicatorOutcome;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class IndicatorDetailFormatter {

  private static final int PERSISTENCE_WINDOW_MONTHS = 4;

  private final DisclosedRiskIndicatorRepository disclosureRepository;
  private final Clock clock;
  private final IndicatorDiagnosticsFormatter diagnosticsFormatter;
  private final RiskClassRangeFormatter rangeFormatter;

  String detailBlock(RiskIndicatorOutcome outcome) {
    var indicator = outcome.indicator();
    var disclosed = disclosedClass(indicator);
    var block = new ArrayList<String>();

    if (!indicator.hasClass()) {
      block.add("⚠️ %s %s — andmeid napib".formatted(indicator.fund(), indicator.indicatorType()));
      block.add(
          "Aknas on %d vaatlust, klassi ei avaldata. Volatiilsus %s."
              .formatted(indicator.latestObservationCount(), rangeFormatter.volatility(indicator)));
      block.add(
          "👉 Tegevus: kontrolli, kas NAV-seeria on täielik. Kui fondi enda ajalugu ongi nõutud"
              + " perioodist lühem, lisa investment.risk.sources alla võrdlusindeksi segment —"
              + " klassi avaldamata jätmine ei ole lubatud variant.");
      return String.join("\n", diagnosticsFormatter.withDiagnostics(block, outcome));
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
                  rangeFormatter.volatilityLabel(indicator),
                  rangeFormatter.volatility(indicator)));
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
                  rangeFormatter.rangeLine(indicator)));
      block.add("👉 Tegevus: kontrolli, kas dokument on juba uuendatud.");
    } else {
      block.add(
          "✅ %s %s — stabiilne, dokument ajakohane"
              .formatted(indicator.fund(), indicator.indicatorType()));
      block.add(rangeFormatter.rangeLine(indicator));
      block.add(majorityLine(indicator));
    }

    return String.join("\n", diagnosticsFormatter.withDiagnostics(block, outcome));
  }

  String mismatchLine(
      PublishedRiskIndicator indicator, @Nullable DisclosedRiskIndicator disclosed) {
    return "🔴 %s %s — dokumendis on klass %s, arvutatud avaldatav klass on %s"
        .formatted(
            indicator.fund(),
            indicator.indicatorType(),
            disclosed == null ? "?" : disclosed.getDisclosedClass(),
            indicator.publishedClass());
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

  @Nullable DisclosedRiskIndicator disclosedClass(PublishedRiskIndicator indicator) {
    return disclosureRepository
        .findFirstByIndicatorTypeAndFundAndDisclosedFromLessThanEqualOrderByDisclosedFromDesc(
            indicator.indicatorType(), indicator.fund(), LocalDate.now(clock))
        .orElse(null);
  }

  boolean isMismatched(
      @Nullable DisclosedRiskIndicator disclosed, PublishedRiskIndicator indicator) {
    return disclosed != null
        && indicator.publishedClass() != null
        && !Objects.equals(disclosed.getDisclosedClass(), indicator.publishedClass());
  }

  Severity severity(PublishedRiskIndicator indicator, @Nullable DisclosedRiskIndicator disclosed) {
    if (isMismatched(disclosed, indicator)) {
      return Severity.RED;
    }
    if (!indicator.hasClass() || disclosed == null) {
      return Severity.YELLOW;
    }
    return indicator.status() == RiskIndicatorStatus.STABLE ? Severity.GREEN : Severity.YELLOW;
  }

  String statusLabel(PublishedRiskIndicator indicator, @Nullable DisclosedRiskIndicator disclosed) {
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

  record TableRow(
      String disclosedClassColumn, String publishedSince, String duration, String statusLabel) {}

  TableRow tableRow(PublishedRiskIndicator indicator) {
    var disclosed = disclosedClass(indicator);
    return new TableRow(
        disclosed == null ? "?" : String.valueOf(disclosed.getDisclosedClass()),
        publishedSince(indicator),
        duration(indicator),
        statusLabel(indicator, disclosed));
  }

  String publishedSince(PublishedRiskIndicator indicator) {
    var since = indicator.publishedSince();
    if (since == null) {
      return "—";
    }
    return indicator.publishedSinceIsTruncated() ? "≥" + since : since.toString();
  }

  String duration(PublishedRiskIndicator indicator) {
    var since = indicator.publishedSince();
    if (since == null) {
      return "—";
    }
    var months = ChronoUnit.MONTHS.between(since, indicator.evaluationDate());
    return months < 12
        ? "%d kuud".formatted(months)
        : "%da %dk".formatted(months / 12, months % 12);
  }

  private String text(@Nullable Object value) {
    return value == null ? "—" : String.valueOf(value);
  }
}
