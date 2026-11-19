package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

record RebalanceMessage(String text, Severity severity) {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  static Optional<RebalanceMessage> of(RebalanceWindow window) {
    return switch (window.state()) {
      case INFO -> Optional.of(inProgress(window));
      case WARNING -> Optional.of(tradesMissing(window));
      case FAIL -> Optional.of(inLineDueMissed(window));
      case OK -> window.closedByTheLatestNav().map(navDate -> closed(window, navDate));
    };
  }

  static RebalanceMessage couldNotRun(TulevaFund fund) {
    return new RebalanceMessage(
        ("⏸ %s mudelportfelli tasakaalustamise kontroll ei jooksnud; põhjus on rakenduse logis,"
                + " kontroll proovib järgmisel tööpäeval uuesti")
            .formatted(fund.getCode()),
        ERROR);
  }

  private static RebalanceMessage inProgress(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        ℹ️ %s: jõustumisest %d päeva, sisemine tähtaeg %s (%d päeva), seaduslik %s
        %s; tehingud: %s"""
            .formatted(
                model(window),
                window.daysSinceModelEffectiveDate(),
                date(window.deadlines().inLineDue()),
                window.daysUntilInLineDue(),
                date(window.deadlines().legal()),
                measurement(window),
                trades(window)),
        INFO);
  }

  private static RebalanceMessage tradesMissing(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        ⚠️ %s: tehingute tähtaeg oli %s, muutunud instrumentides tehinguid pole
        %s
        Tee tasakaalustamise tehingud; sisemine tähtaeg %s, seaduslik %s"""
            .formatted(
                model(window),
                date(window.deadlines().tradesDue()),
                measurement(window),
                date(window.deadlines().inLineDue()),
                date(window.deadlines().legal())),
        WARNING);
  }

  private static RebalanceMessage inLineDueMissed(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        🛑 %s: sisemine tähtaeg oli %s, fond pole mudeliga vastavuses
        %s; tehingud: %s
        Vii fond %s pp piiresse; %s (sisekord 4 p 2.6.1)"""
            .formatted(
                model(window),
                date(window.deadlines().inLineDue()),
                measurement(window),
                trades(window),
                points(window.tolerance()),
                legalDeadline(window)),
        ERROR);
  }

  private static RebalanceMessage closed(RebalanceWindow window, LocalDate closingNavDate) {
    return new RebalanceMessage(
        "✅ %s: fond on mudeliga vastavuses (kõik osakaalud %s pp piires, NAV %s), kontroll suletud"
            .formatted(model(window), points(window.tolerance()), date(closingNavDate)),
        INFO);
  }

  private static String model(RebalanceWindow window) {
    return "%s mudelportfell alates %s"
        .formatted(window.fund().getCode(), date(window.modelEffectiveDate()));
  }

  private static String measurement(RebalanceWindow window) {
    var latestNavDate = window.latestNavDate();
    if (latestNavDate == null) {
      return "NAV alates %s pole veel avaldatud".formatted(date(window.modelEffectiveDate()));
    }
    return "Üle %s pp (NAV %s): %s"
        .formatted(
            points(window.tolerance()),
            date(latestNavDate),
            window.outsideToleranceAtLatestNav().stream()
                .map(
                    deviation ->
                        deviation.isin() + " " + signedPoints(deviation.percentagePoints()) + " pp")
                .collect(joining(", ")));
  }

  private static String trades(RebalanceWindow window) {
    return window.tradesDone()
        ? window.tradedIsins().stream().sorted().collect(joining(", "))
        : "pole";
  }

  private static String legalDeadline(RebalanceWindow window) {
    var legal = window.deadlines().legal();
    return window.checkDate().isAfter(legal)
        ? "seaduslik tähtaeg oli " + date(legal)
        : "seaduslik tähtaeg " + date(legal);
  }

  private static String points(BigDecimal weight) {
    return weight.movePointRight(2).stripTrailingZeros().toPlainString().replace('.', ',');
  }

  private static String signedPoints(BigDecimal percentagePoints) {
    return String.format(Locale.ROOT, "%+.2f", percentagePoints).replace('.', ',');
  }

  private static String date(LocalDate date) {
    return date.format(DATE);
  }
}
